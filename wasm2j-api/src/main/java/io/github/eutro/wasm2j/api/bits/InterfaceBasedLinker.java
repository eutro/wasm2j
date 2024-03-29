package io.github.eutro.wasm2j.api.bits;

import io.github.eutro.jwasm.tree.AbstractImportNode;
import io.github.eutro.jwasm.tree.ExportNode;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.api.ModuleCompilation;
import io.github.eutro.wasm2j.api.WasmCompiler;
import io.github.eutro.wasm2j.api.events.EmitClassEvent;
import io.github.eutro.wasm2j.api.events.EventSupplier;
import io.github.eutro.wasm2j.api.events.ModifyConventionsEvent;
import io.github.eutro.wasm2j.api.events.RunModuleCompilationEvent;
import io.github.eutro.wasm2j.api.types.ExternType;
import io.github.eutro.wasm2j.api.support.NameSupplier;
import io.github.eutro.wasm2j.api.types.ValType;
import io.github.eutro.wasm2j.core.util.Getters;
import io.github.eutro.wasm2j.core.conf.itf.*;
import io.github.eutro.wasm2j.core.conf.impl.*;
import io.github.eutro.wasm2j.core.ext.JavaExts;
import io.github.eutro.wasm2j.core.ops.CommonOps;
import io.github.eutro.wasm2j.core.ops.JavaOps;
import io.github.eutro.wasm2j.core.ops.WasmOps;
import io.github.eutro.wasm2j.core.ssa.Module;
import io.github.eutro.wasm2j.core.ssa.*;
import io.github.eutro.wasm2j.core.util.IRUtils;
import io.github.eutro.wasm2j.core.util.ValueGetter;
import io.github.eutro.wasm2j.core.util.ValueGetterSetter;
import io.github.eutro.wasm2j.core.util.ValueSetter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static io.github.eutro.wasm2j.core.util.Lazy.lazy;

/**
 * A linker for WebAssembly modules that generates Java interfaces for every {@link #register(String, ModuleNode)
 * registered}, or unregistered but imported, module. Implementations of imported modules are then to be passed
 * to the constructors of each module that imports it.
 *
 * @param <T> The type of compiler this is attached to. Most likely {@link WasmCompiler}.
 */
public class InterfaceBasedLinker<T extends EventSupplier<? super RunModuleCompilationEvent>>
        extends EventSupplier<EmitClassEvent>
        implements Bit<T, InterfaceBasedLinker<T>> {
    private final NameSupplier names;

    /**
     * Construct an interface-based linker with the given supplier for names.
     * <p>
     * The name supplier will be used to generate the names of interface classes,
     * and for the methods in the interfaces.
     *
     * @param names The name supplier.
     */
    public InterfaceBasedLinker(NameSupplier names) {
        this.names = names;
    }

    private final Map<String, ModuleInterface> modules = new ConcurrentHashMap<>();
    private final Map<ModuleNode, GeneratedCode> generatedCode = new ConcurrentHashMap<>();

    private static class GeneratedCode {
        CompletableFuture<ClassNode> input = new CompletableFuture<>();
        boolean isDelivered = false;

        public ClassNode getInput() {
            return input.getNow(null);
        }
    }

    private class ModuleInterface implements Comparable<ModuleInterface> {
        private final Map<String, ExternType> exports = new ConcurrentHashMap<>();
        private final Map<String, List<ExternType>> imports = new ConcurrentHashMap<>();
        private @Nullable GeneratedCode implementation = null;
        private final String name;
        JClass jClass = null;

        public ModuleInterface(String moduleName) {
            name = moduleName;
        }

        JClass getJClass() {
            if (jClass == null) {
                jClass = new JClass(getClassName());
                jClass.access |= Opcodes.ACC_INTERFACE;
            }
            return jClass;
        }

        Optional<Map<String, ExternType>> getInterfaceTypes() {
            Map<String, ExternType> interfaceTypes = implementation == null ? new HashMap<>() : exports;
            for (Map.Entry<String, List<ExternType>> entry : imports.entrySet()) {
                if (implementation != null) {
                    if (!exports.containsKey(entry.getKey())) {
                        return Optional.empty();
                    }
                }
                ExternType ty = ExternType.Top.INSTANCE;
                for (ExternType externType : entry.getValue()) {
                    ty = ExternType.intersect(ty, externType);
                    if (ty == null) {
                        return Optional.empty();
                    }
                }
                if (implementation != null) {
                    if (!ty.assignableFrom(exports.get(entry.getKey()))) {
                        return Optional.empty();
                    }
                } else {
                    interfaceTypes.put(entry.getKey(), ty);
                }
            }
            return Optional.of(interfaceTypes);
        }

        void requireImport(String importName, ExternType type) {
            imports.computeIfAbsent(importName, $ -> Collections.synchronizedList(new ArrayList<>()))
                    .add(type);
        }

        void addImplementation(ModuleNode node, GeneratedCode cf) {
            if (implementation != null) {
                throw new IllegalArgumentException("duplicate implementation for module " + name);
            }
            implementation = cf;
            if (node.exports != null) {
                EnumMap<ExternType.Kind, List<ExternType>> imported = new EnumMap<>(ExternType.Kind.class);
                if (node.imports != null) {
                    for (AbstractImportNode anImport : node.imports) {
                        ExternType ty = ExternType.fromImport(anImport, node);
                        imported.computeIfAbsent(ty.getKind(), $ -> new ArrayList<>())
                                .add(ty);
                    }
                }
                for (ExportNode export : node.exports) {
                    ExternType.Kind kind = ExternType.Kind.fromByte(export.type);
                    List<ExternType> importedOfKind = imported.getOrDefault(kind, Collections.emptyList());
                    ExternType exportType = export.index < importedOfKind.size()
                            ? importedOfKind.get(export.index)
                            : ExternType.getLocal(node, kind, export.index - importedOfKind.size());
                    exports.put(export.name, exportType);
                }
            }
        }

        public ClassNode generateCode() {
            Map<String, ExternType> interfaceTypes = getInterfaceTypes()
                    .orElseThrow(IllegalArgumentException::new);
            ClassNode cn = new ClassNode();
            cn.visit(
                    Opcodes.V1_8,
                    Opcodes.ACC_INTERFACE | Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                    getClassName(),
                    null,
                    "java/lang/Object",
                    null
            );
            for (Map.Entry<String, ExternType> entry : interfaceTypes.entrySet()) {
                ExternType ty = entry.getValue();
                switch (Objects.requireNonNull(ty.getKind())) {
                    case FUNC: {
                        ExternType.Func fTy = (ExternType.Func) ty;
                        cn.visitMethod(
                                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                                names.fieldName(entry.getKey()),
                                fTy.getMethodType().toMethodDescriptorString(),
                                null,
                                null
                        ).visitEnd();
                        break;
                    }
                    case TABLE:
                    case MEM:
                    case GLOBAL: {
                        Type valTy;
                        boolean isMut;
                        switch (ty.getKind()) {
                            case TABLE: {
                                ExternType.Table tTy = (ExternType.Table) ty;
                                valTy = Type.getType("[" + tTy.componentType.getAsmType().getDescriptor());
                                isMut = true;
                                break;
                            }
                            case MEM: {
                                valTy = Type.getType(ByteBuffer.class);
                                isMut = true;
                                break;
                            }
                            case GLOBAL: {
                                ExternType.Global gTy = (ExternType.Global) ty;
                                valTy = gTy.type.getAsmType();
                                isMut = gTy.isMut;
                                break;
                            }
                            default:
                                throw new AssertionError();
                        }
                        cn.visitMethod(
                                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                                names.fieldName("get", entry.getKey()),
                                Type.getMethodDescriptor(valTy),
                                null,
                                null
                        );
                        if (isMut) {
                            cn.visitMethod(
                                    Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                                    names.fieldName("set", entry.getKey()),
                                    Type.getMethodDescriptor(Type.VOID_TYPE, valTy),
                                    null,
                                    null
                            );
                        }
                        break;
                    }
                }
            }
            return cn;
        }

        private String getClassName() {
            return names.className(name);
        }

        @Override
        public int compareTo(@NotNull ModuleInterface o) {
            return name.compareTo(o.name);
        }
    }

    @FunctionalInterface
    interface LookupCb<T extends EventSupplier<? super RunModuleCompilationEvent>, R> {
        R call(int modIdx, InterfaceBasedLinker<T>.ModuleInterface itf);
    }

    <R> R lookupItf(SortedSet<ModuleInterface> importedModules, AbstractImportNode importNode, LookupCb<T, R> f) {
        ModuleInterface itf = new ModuleInterface(importNode.module);
        int modIdx = importedModules.headSet(itf).size();
        itf = importedModules.tailSet(itf).first();
        assert itf.name.equals(importNode.module);
        return f.call(modIdx, itf);
    }

    ExportableConvention exporter(ValueGetter target, Map<String, JClass.JavaMethod> delegates) {
        return (node, module, jClass) -> {
            for (Map.Entry<String, JClass.JavaMethod> entry : delegates.entrySet()) {
                JClass.JavaMethod method = entry.getValue();
                JClass.JavaMethod jMethod = new JClass.JavaMethod(
                        jClass,
                        entry.getKey() == null
                                ? names.fieldName(node.name)
                                : names.fieldName(entry.getKey(), node.name),
                        method.getDescriptor(),
                        Modifier.PUBLIC
                );
                jClass.methods.add(jMethod);
                jMethod.attachExt(JavaExts.METHOD_IMPL, lazy(() -> {
                    Function func = new Function();
                    func.attachExt(JavaExts.FUNCTION_METHOD, jMethod);
                    IRBuilder ib = new IRBuilder(func, func.newBb());
                    List<Var> args = new ArrayList<>();
                    args.add(target.get(ib));
                    int paramCount = method.getParamTys().size();
                    for (int i = 0; i < paramCount; ++i) {
                        args.add(ib.insert(CommonOps.ARG.create(i).insn(), ib.func.newVar("arg", i)));
                    }
                    List<Var> rets = new ArrayList<>();
                    if (!method.getReturnTy().equals(Type.VOID_TYPE)) {
                        rets.add(func.newVar("ret"));
                    }
                    ib.insert(JavaOps.INVOKE.create(method)
                            .insn(args)
                            .assignTo(rets));
                    ib.insertCtrl(CommonOps.RETURN.insn(rets).jumpsTo());
                    return func;
                }));
            }
        };
    }

    ExportableConvention exporter(ValueGetter target, JClass.JavaMethod method) {
        return exporter(target, Collections.singletonMap(null, method));
    }

    ExportableConvention exporter(ValueGetter target, JClass.JavaMethod getter, JClass.JavaMethod setter) {
        HashMap<String, JClass.JavaMethod> map = new HashMap<>();
        if (getter != null) map.put("get", getter);
        if (setter != null) map.put("set", setter);
        return exporter(target, map);
    }

    ExportableConvention exporter(Type type, ValueGetter getter, ValueSetter setter) {
        return (node, module, jClass) -> {
            JClass.JavaMethod getMethod = new JClass.JavaMethod(
                    jClass,
                    names.fieldName("get", node.name),
                    Type.getMethodDescriptor(type),
                    Modifier.PUBLIC
            );
            jClass.methods.add(getMethod);
            JClass.JavaMethod setMethod = new JClass.JavaMethod(
                    jClass,
                    names.fieldName("set", node.name),
                    Type.getMethodDescriptor(Type.VOID_TYPE, type),
                    Modifier.PUBLIC
            );
            jClass.methods.add(setMethod);
            getMethod.attachExt(JavaExts.METHOD_IMPL, lazy(() -> {
                Function func = new Function();
                func.attachExt(JavaExts.FUNCTION_METHOD, getMethod);
                IRBuilder ib = new IRBuilder(func, func.newBb());
                Var got = getter.get(ib);
                ib.insertCtrl(CommonOps.RETURN.insn(got).jumpsTo());
                return func;
            }));
            setMethod.attachExt(JavaExts.METHOD_IMPL, lazy(() -> {
                Function func = new Function();
                func.attachExt(JavaExts.FUNCTION_METHOD, setMethod);
                IRBuilder ib = new IRBuilder(func, func.newBb());
                Var newValue = ib.insert(CommonOps.ARG.create(0).insn(), "arg");
                setter.set(ib, newValue);
                ib.insertCtrl(CommonOps.RETURN.insn().jumpsTo());
                return func;
            }));
        };
    }

    interface GetterSetterCb<T> {
        T call(ValueGetterSetter target, JClass.JavaMethod getter, JClass.JavaMethod setter);
    }

    @NotNull
    private <R> R importGetterSetter(AbstractImportNode importNode,
                                     JClass.JavaField field,
                                     ModuleInterface itf,
                                     Type asmType,
                                     GetterSetterCb<R> cb) {
        ValueGetterSetter target = Getters.fieldGetter(Getters.GET_THIS, field);
        JClass.JavaMethod getter = new JClass.JavaMethod(
                itf.getJClass(),
                names.fieldName("get", importNode.name),
                Type.getMethodDescriptor(asmType),
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT
        );
        JClass.JavaMethod setter = new JClass.JavaMethod(
                itf.getJClass(),
                names.fieldName("set", importNode.name),
                Type.getMethodDescriptor(Type.VOID_TYPE, asmType),
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT
        );
        return cb.call(target, getter, setter);
    }

    /**
     * Register a module with this linker, generating an interface for its exports.
     * <p>
     * This may be called before or after the module is submitted to the compiler,
     * but must be called before {@link #finish()}.
     * <p>
     * Any imports for the module with the given name will resolve to the supplied module.
     * That is, they will by type checked against the exports provided by the module, rather than
     * against other importers of the same module.
     *
     * @param moduleName The module's name.
     * @param node       The module node.
     */
    public void register(String moduleName, ModuleNode node) {
        modules.computeIfAbsent(moduleName, ModuleInterface::new)
                .addImplementation(node,
                        generatedCode.computeIfAbsent(node, $ -> new GeneratedCode()));
    }

    SortedSet<ModuleInterface> requireImports(ModuleNode module) {
        TreeSet<ModuleInterface> importedModules = new TreeSet<>();
        if (module.imports != null) {
            for (AbstractImportNode anImport : module.imports) {
                ExternType type = ExternType.fromImport(anImport, module);
                ModuleInterface moduleInterface = modules.computeIfAbsent(anImport.module, ModuleInterface::new);
                moduleInterface.requireImport(anImport.name, type);
                importedModules.add(moduleInterface);
            }
        }
        return importedModules;
    }

    /**
     * Finish linking, generating all interfaces for imported modules,
     * finishing up compiled modules, and emitting {@link EmitClassEvent}s for each.
     */
    public void finish() {
        for (ModuleInterface interfacedModule : modules.values()) {
            ClassNode interfaceCode = interfacedModule.generateCode();
            dispatch(EmitClassEvent.class, new EmitClassEvent(interfaceCode));
            if (interfacedModule.implementation != null) {
                ClassNode input = interfacedModule.implementation.getInput();
                input.interfaces.add(interfaceCode.name);
                dispatch(EmitClassEvent.class, new EmitClassEvent(input));
                interfacedModule.implementation.isDelivered = true;
            }
        }
        for (GeneratedCode code : generatedCode.values()) {
            if (!code.isDelivered) {
                dispatch(EmitClassEvent.class, new EmitClassEvent(code.getInput()));
                code.isDelivered = true;
            }
        }
    }

    /**
     * Add this to a compiler, listening to it for {@link RunModuleCompilationEvent}s,
     * and adding their imported modules to the linked set.
     *
     * @param cc The compiler.
     * @return This.
     */
    @Override
    public InterfaceBasedLinker<T> addTo(T cc) {
        cc.listen(RunModuleCompilationEvent.class, evt -> {
            ModuleCompilation compilation = evt.compilation;
            SortedSet<ModuleInterface> importedModules = requireImports(compilation.node);
            List<JClass.JavaField> fields = importedModules.stream()
                    .map(itf -> new JClass.JavaField(null, "_imported_" + itf.name,
                            "L" + itf.getClassName() + ";",
                            false))
                    .collect(Collectors.toList());
            compilation.listen(ModifyConventionsEvent.class, mce -> mce.conventionBuilder
                    .addConstructorCallback((ConstructorCallback.Abstract) (ib, ctor, module, jClass) -> {
                        List<Type> paramTys = ctor.getParamTys();
                        int arg = 0;
                        for (JClass.JavaField field : fields) {
                            field.owner = jClass;
                            jClass.fields.add(field);
                            ib.insert(JavaOps.PUT_FIELD.create(field)
                                    .insn(IRUtils.getThis(ib),
                                            ib.insert(CommonOps.ARG.create(arg++).insn(),
                                                    "arg"))
                                    .assignTo());
                        }
                        for (ModuleInterface importedModule : importedModules) {
                            paramTys.add(Type.getObjectType(importedModule.getClassName()));
                        }
                    })
                    .setModifyFuncConvention((functionConvention, funcNodeCodeNodePair, index) ->
                            new FunctionConvention.Delegating(functionConvention) {
                                @Override
                                public void export(ExportNode node, Module module, JClass jClass) {
                                    super.export(node, module, jClass);
                                    ValueGetter target = functionConvention
                                            .getExtOrThrow(InstanceFunctionConvention.CONVENTION_TARGET);
                                    JClass.JavaMethod method = functionConvention
                                            .getExtOrThrow(InstanceFunctionConvention.CONVENTION_METHOD);
                                    exporter(target, method).export(node, module, jClass);
                                }
                            })
                    .setModifyGlobalConvention((globalConvention, globalNode, index) ->
                            new GlobalConvention.Delegating(globalConvention) {
                                @Override
                                public void export(ExportNode node, Module module, JClass jClass) {
                                    super.export(node, module, jClass);
                                    exporter(
                                            ValType.fromOpcode(globalNode.type.type).getAsmType(),
                                            ib -> {
                                                Var ret = ib.func.newVar("ret");
                                                emitGlobalRef(ib, WasmOps.GLOBAL_REF
                                                        .create(index)
                                                        .insn()
                                                        .assignTo(ret));
                                                return ret;
                                            },
                                            (ib, val) -> emitGlobalStore(ib, WasmOps.GLOBAL_SET
                                                    .create(index)
                                                    .insn(val)
                                                    .assignTo())
                                    ).export(node, module, jClass);
                                }
                            })
                    .setModifyMemConvention((memoryConvention, memoryNode, index) ->
                            new MemoryConvention.Delegating(memoryConvention) {
                                @Override
                                public void export(ExportNode node, Module module, JClass jClass) {
                                    super.export(node, module, jClass);
                                    ValueGetterSetter byteBuf = getExtOrThrow(ByteBufferMemoryConvention.MEMORY_BYTE_BUFFER);
                                    exporter(Type.getType(ByteBuffer.class), byteBuf, byteBuf)
                                            .export(node, module, jClass);
                                }
                            })
                    .setModifyTableConvention((tableConvention, tableNode, index) ->
                            new TableConvention.Delegating(tableConvention) {
                                @Override
                                public void export(ExportNode node, Module module, JClass jClass) {
                                    super.export(node, module, jClass);
                                    ValueGetterSetter array = getExtOrThrow(ArrayTableConvention.TABLE_ARRAY);
                                    exporter(ValType.fromOpcode(tableNode.type).getAsmType(), array, array)
                                            .export(node, module, jClass);
                                }
                            })
                    .setFunctionImports((module, importNode, jClass, idx) ->
                            lookupItf(importedModules, importNode, (modIdx, itf) -> {
                                JClass.JavaMethod method = new JClass.JavaMethod(
                                        itf.getJClass(),
                                        names.fieldName(importNode.name),
                                        ((ExternType.Func) ExternType.fromImport(importNode, compilation.node))
                                                .getMethodType().toMethodDescriptorString(),
                                        Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT
                                );
                                return new InstanceFunctionConvention(
                                        exporter(Getters.GET_THIS, method),
                                        Getters.fieldGetter(Getters.GET_THIS, fields.get(modIdx)),
                                        method,
                                        BasicCallingConvention.INSTANCE);
                            }))
                    .setGlobalImports((module, importNode, jClass, idx) ->
                            lookupItf(importedModules, importNode, (modIdx, itf) -> {
                                ExternType.Global ty = (ExternType.Global) ExternType
                                        .fromImport(importNode, compilation.node);
                                Type asmType = ty.type.getAsmType();
                                return importGetterSetter(
                                        importNode,
                                        fields.get(modIdx),
                                        itf,
                                        asmType,
                                        (target, getter, setter) ->
                                                new GetterSetterGlobalConvention(
                                                        exporter(target, getter, setter),
                                                        Getters.methodGetterSetter(target, getter, setter)
                                                )
                                );
                            }))
                    .setMemoryImports((module, importNode, jClass, idx) ->
                            lookupItf(importedModules, importNode, (modIdx, itf) ->
                                    importGetterSetter(importNode, fields.get(modIdx), itf,
                                            Type.getType(ByteBuffer.class),
                                            (target, getter, setter) ->
                                                    new ByteBufferMemoryConvention(
                                                            exporter(target, getter, setter),
                                                            Getters.methodGetterSetter(target, getter, setter),
                                                            importNode.limits.max
                                                    ))))
                    .setTableImports((module, importNode, jClass, idx) ->
                            lookupItf(importedModules, importNode, (modIdx, itf) -> {
                                ExternType.Table ty = (ExternType.Table) ExternType
                                        .fromImport(importNode, compilation.node);
                                Type componentType = ty.componentType.getAsmType();
                                return importGetterSetter(
                                        importNode,
                                        fields.get(modIdx),
                                        itf,
                                        Type.getType("[" + componentType.getDescriptor()),
                                        (target, getter, setter) ->
                                                new ArrayTableConvention(
                                                        exporter(target, getter, setter),
                                                        Getters.methodGetterSetter(target, getter, setter),
                                                        componentType,
                                                        ty.limits.max
                                                )
                                );
                            })));
            GeneratedCode gen = generatedCode.computeIfAbsent(compilation.node, $ -> new GeneratedCode());
            compilation.listen(EmitClassEvent.class, ece -> gen.input.complete(ece.classNode));
        });
        return this;
    }
}

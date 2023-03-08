package io.github.eutro.wasm2j.bits;

import io.github.eutro.jwasm.tree.AbstractImportNode;
import io.github.eutro.jwasm.tree.ExportNode;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.ModuleCompilation;
import io.github.eutro.wasm2j.conf.Conventions;
import io.github.eutro.wasm2j.conf.Getters;
import io.github.eutro.wasm2j.conf.api.ConstructorCallback;
import io.github.eutro.wasm2j.conf.api.ExportableConvention;
import io.github.eutro.wasm2j.conf.api.FunctionConvention;
import io.github.eutro.wasm2j.conf.impl.ArrayTableConvention;
import io.github.eutro.wasm2j.conf.impl.ByteBufferMemoryConvention;
import io.github.eutro.wasm2j.conf.impl.GetterSetterGlobalConvention;
import io.github.eutro.wasm2j.conf.impl.InstanceFunctionConvention;
import io.github.eutro.wasm2j.events.EmitClassEvent;
import io.github.eutro.wasm2j.events.EventSupplier;
import io.github.eutro.wasm2j.events.ModifyConventionsEvent;
import io.github.eutro.wasm2j.events.RunModuleCompilationEvent;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.Var;
import io.github.eutro.wasm2j.support.ExternType;
import io.github.eutro.wasm2j.support.NameSupplier;
import io.github.eutro.wasm2j.util.IRUtils;
import io.github.eutro.wasm2j.util.ValueGetter;
import io.github.eutro.wasm2j.util.ValueGetterSetter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InterfaceBasedLinker<T extends EventSupplier<? super RunModuleCompilationEvent>>
        extends EventSupplier<EmitClassEvent>
        implements Bit<T, InterfaceBasedLinker<T>> {
    private final NameSupplier names;

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
        JavaExts.JavaClass jClass = null;

        public ModuleInterface(String moduleName) {
            name = moduleName;
        }

        JavaExts.JavaClass getJClass() {
            if (jClass == null) {
                jClass = new JavaExts.JavaClass(getClassName());
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
                                valTy = Type.getType("[" + tTy.refType.getAsmType().getDescriptor());
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

    ExportableConvention exporter(ValueGetter target, Map<String, JavaExts.JavaMethod> delegates) {
        return (node, module, jClass) -> {
            for (Map.Entry<String, JavaExts.JavaMethod> entry : delegates.entrySet()) {
                JavaExts.JavaMethod method = entry.getValue();
                Function func = new Function();
                JavaExts.JavaMethod jMethod = new JavaExts.JavaMethod(
                        jClass,
                        entry.getKey() == null
                                ? names.fieldName(node.name)
                                : names.fieldName(entry.getKey(), node.name),
                        method.getDescriptor(),
                        JavaExts.JavaMethod.Kind.VIRTUAL
                );
                jClass.methods.add(jMethod);
                jMethod.attachExt(JavaExts.METHOD_IMPL, func);
                func.attachExt(JavaExts.FUNCTION_OWNER, jClass);
                func.attachExt(JavaExts.FUNCTION_METHOD, jMethod);
                module.functions.add(func);
                IRBuilder ib = new IRBuilder(func, func.newBb());
                List<Var> args = new ArrayList<>();
                args.add(target.get(ib));
                int paramCount = method.getParamTys().size();
                for (int i = 0; i < paramCount; ++i) {
                    args.add(ib.insert(CommonOps.ARG.create(i).insn(), "arg" + i));
                }
                List<Var> rets = new ArrayList<>();
                if (!method.getReturnTy().equals(Type.VOID_TYPE)) {
                    rets.add(func.newVar("ret"));
                }
                ib.insert(JavaOps.INVOKE.create(method)
                        .insn(args)
                        .assignTo(rets));
                ib.insertCtrl(CommonOps.RETURN.insn(rets).jumpsTo());
            }
        };
    }

    ExportableConvention exporter(ValueGetter target, JavaExts.JavaMethod method) {
        return exporter(target, Collections.singletonMap(null, method));
    }

    ExportableConvention exporter(ValueGetter target, JavaExts.JavaMethod getter, JavaExts.JavaMethod setter) {
        HashMap<String, JavaExts.JavaMethod> map = new HashMap<>();
        if (getter != null) map.put("get", getter);
        if (setter != null) map.put("set", setter);
        return exporter(target, map);
    }

    interface GetterSetterCb<T> {
        T call(ValueGetterSetter target, JavaExts.JavaMethod getter, JavaExts.JavaMethod setter);
    }

    @NotNull
    private <R> R importGetterSetter(AbstractImportNode importNode,
                                     JavaExts.JavaField field,
                                     ModuleInterface itf,
                                     Type asmType,
                                     GetterSetterCb<R> cb) {
        ValueGetterSetter target = Getters.fieldGetter(Getters.GET_THIS, field);
        JavaExts.JavaMethod getter = new JavaExts.JavaMethod(
                itf.getJClass(),
                names.fieldName("get", importNode.name),
                Type.getMethodDescriptor(asmType),
                JavaExts.JavaMethod.Kind.INTERFACE
        );
        JavaExts.JavaMethod setter = new JavaExts.JavaMethod(
                itf.getJClass(),
                names.fieldName("set", importNode.name),
                Type.getMethodDescriptor(Type.VOID_TYPE, asmType),
                JavaExts.JavaMethod.Kind.INTERFACE
        );
        return cb.call(target, getter, setter);
    }

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

    @Override
    public InterfaceBasedLinker<T> addTo(T cc) {
        cc.listen(RunModuleCompilationEvent.class, evt -> {
            ModuleCompilation compilation = evt.compilation;
            SortedSet<ModuleInterface> importedModules = requireImports(compilation.node);
            List<JavaExts.JavaField> fields = importedModules.stream()
                    .map(itf -> new JavaExts.JavaField(null, "_imported_" + itf.name,
                            "L" + itf.getClassName() + ";",
                            false))
                    .collect(Collectors.toList());
            compilation.listen(ModifyConventionsEvent.class, mce -> mce.conventionBuilder
                    .addConstructorCallback((ConstructorCallback.Abstract) (ib, ctor, module, jClass) -> {
                        List<Type> paramTys = ctor.getParamTys();
                        int arg = 0;
                        for (JavaExts.JavaField field : fields) {
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
                                public void export(ExportNode node, Module module, JavaExts.JavaClass jClass) {
                                    InstanceFunctionConvention fc = functionConvention
                                            .getExtOrThrow(InstanceFunctionConvention.FUNCTION_CONVENTION);
                                    exporter(fc.target, fc.method).export(node, module, jClass);
                                }
                            })
                    .setFunctionImports((module, importNode, jClass, idx) ->
                            lookupItf(importedModules, importNode, (modIdx, itf) -> {
                                JavaExts.JavaMethod method = new JavaExts.JavaMethod(
                                        itf.getJClass(),
                                        names.fieldName(importNode.name),
                                        ((ExternType.Func) ExternType.fromImport(importNode, compilation.node))
                                                .getMethodType().toMethodDescriptorString(),
                                        JavaExts.JavaMethod.Kind.INTERFACE
                                );
                                return new InstanceFunctionConvention(
                                        exporter(Getters.GET_THIS, method),
                                        Getters.fieldGetter(Getters.GET_THIS, fields.get(modIdx)),
                                        method,
                                        Conventions.DEFAULT_CC);
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
                                Type componentType = ty.refType.getAsmType();
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

package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.sexp.Parser;
import io.github.eutro.jwasm.sexp.Reader;
import io.github.eutro.jwasm.tree.*;
import io.github.eutro.wasm2j.conf.Conventions;
import io.github.eutro.wasm2j.conf.Getters;
import io.github.eutro.wasm2j.conf.api.CallingConvention;
import io.github.eutro.wasm2j.conf.api.ExportableConvention;
import io.github.eutro.wasm2j.conf.api.FunctionConvention;
import io.github.eutro.wasm2j.conf.api.ImportFactory;
import io.github.eutro.wasm2j.conf.impl.InstanceFunctionConvention;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ext.WasmExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.passes.Passes;
import io.github.eutro.wasm2j.passes.convert.JirToJava;
import io.github.eutro.wasm2j.passes.convert.WasmToWir;
import io.github.eutro.wasm2j.passes.convert.WirToJir;
import io.github.eutro.wasm2j.passes.meta.CheckJava;
import io.github.eutro.wasm2j.passes.misc.ForPass;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.util.IRUtils;
import io.github.eutro.wasm2j.util.ValueGetter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static io.github.eutro.wasm2j.conf.api.ExportableConvention.mangle;

public class WebAssembly {

    public static final JavaExts.JavaClass EV_CLASS = new JavaExts.JavaClass(Type.getInternalName(ExternVal.class));

    private File debugOutput;
    public void setDebugOutputDirectory(File file) {
        this.debugOutput = file;
    }

    public Store storeInit() {
        return new Store();
    }

    public Module moduleDecode(byte[] bytes) {
        ModuleNode node = new ModuleNode();
        ModuleReader.fromBytes(bytes).accept(node);
        return new Module(node);
    }

    public Module moduleParse(String source) {
        List<Object> objs = Reader.readAll(source);
        if (objs.size() != 1) throw new IllegalArgumentException("Too many modules in string");
        ModuleNode node = Parser.parseModule(objs.get(0));
        return new Module(node);
    }

    public Module moduleFromNode(ModuleNode node) {
        // protect from mutation, since that could break validation
        ModuleNode copiedNode = new ModuleNode();
        node.accept(copiedNode);
        return new Module(copiedNode);
    }

    public void moduleValidate(Module module) {
        module.validate();
    }

    private final AtomicInteger counter = new AtomicInteger(0);

    private final Ext<Map<String, ValueGetter>> EXPORTS_EXT = Ext.create(Map.class);
    private final IRPass<ModuleNode, ClassNode> PASS = WasmToWir.INSTANCE
            .then(new WirToJir(Conventions.createBuilder()
                    .setNameSupplier(() -> {
                        String thisClassName = WebAssembly.class.getName();
                        return thisClassName
                                .substring(0, thisClassName.lastIndexOf('.'))
                                .replace('.', '/')
                                + "/Module" + counter.getAndIncrement();
                    })
                    .setFunctionImports(new ImportFactory<FuncImportNode, FunctionConvention>() {
                        final List<JavaExts.JavaField> fields = new ArrayList<>();
                        final List<Type> types = new ArrayList<>();
                        int counter = 0;

                        @Override
                        public FunctionConvention createImport(io.github.eutro.wasm2j.ssa.Module module,
                                                               FuncImportNode importNode,
                                                               JavaExts.JavaClass jClass) {
                            ModuleNode node = module.getExtOrThrow(WasmExts.MODULE);
                            TypeNode type = Objects.requireNonNull(Objects.requireNonNull(node.types).types).get(importNode.type);

                            JavaExts.JavaField field = new JavaExts.JavaField(jClass,
                                    mangle("f" + counter++ + '_' + importNode.module + '_' + importNode.name),
                                    Type.getDescriptor(MethodHandle.class),
                                    false);
                            jClass.fields.add(field);
                            field.otherAccess |= Opcodes.ACC_FINAL;
                            CallingConvention cc = Conventions.DEFAULT_CC; // FIXME get the correct CC
                            ValueGetter getHandle = Getters.fieldGetter(
                                    Getters.GET_THIS,
                                    field
                            );
                            Type descriptor = cc.getDescriptor(type);
                            fields.add(field);
                            types.add(descriptor);
                            return new InstanceFunctionConvention(
                                    ExportableConvention.fieldExporter(field),
                                    getHandle,
                                    new JavaExts.JavaMethod(
                                            IRUtils.METHOD_HANDLE_CLASS,
                                            "invokeExact",
                                            descriptor.getDescriptor(),
                                            JavaExts.JavaMethod.Kind.VIRTUAL
                                    ),
                                    cc
                            ) {
                                // don't just try to bind invokeExact...
                                @Override
                                public void emitFuncRef(IRBuilder ib, Effect effect) {
                                    ib.insert(CommonOps.IDENTITY.insn(getHandle.get(ib)).copyFrom(effect));
                                }
                            };
                        }

                        @Override
                        public void modifyConstructor(IRBuilder ib, JavaExts.JavaMethod ctorMethod, JavaExts.JavaClass jClass) {
                            JavaExts.JavaClass mtyClass = new JavaExts.JavaClass(Type.getInternalName(MethodType.class));
                            JavaExts.JavaMethod mtyMethodType = new JavaExts.JavaMethod(
                                    mtyClass,
                                    "methodType",
                                    Type.getMethodType(
                                            Type.getType(MethodType.class),
                                            Type.getType(Class.class),
                                            Type.getType(Class[].class)
                                    ).getDescriptor(),
                                    JavaExts.JavaMethod.Kind.STATIC
                            );
                            JavaExts.JavaMethod evGetAsFunc = new JavaExts.JavaMethod(EV_CLASS, "getAsFunc",
                                    Type.getMethodDescriptor(
                                            Type.getType(MethodHandle.class),
                                            Type.getType(MethodType.class)
                                    ),
                                    JavaExts.JavaMethod.Kind.VIRTUAL);

                            List<Type> params = ctorMethod.getParamTys();
                            int i = 0;
                            for (JavaExts.JavaField field : fields) {
                                Type type = types.get(i++);
                                int argIdx = params.size();
                                params.add(Type.getType(ExternVal.class));
                                Var importVal = ib.insert(CommonOps.ARG.create(argIdx).insn(), "import");
                                Var returnClass = ib.insert(IRUtils.loadClass(type.getReturnType()), "retTy");

                                Type[] argTys = type.getArgumentTypes();
                                Var paramClasses = ib.insert(JavaOps.insns(new TypeInsnNode(
                                                        Opcodes.ANEWARRAY,
                                                        Type.getInternalName(Class.class)
                                                ))
                                                .insn(ib.insert(CommonOps.CONST.create(argTys.length).insn(), "len")),
                                        "paramTys");
                                for (int j = 0; j < argTys.length; j++) {
                                    ib.insert(JavaOps.ARRAY_SET.create().insn(
                                            paramClasses,
                                            ib.insert(CommonOps.CONST.create(j).insn(), "i"),
                                            ib.insert(IRUtils.loadClass(argTys[j]), "pty")
                                    ).assignTo());
                                }

                                Var mty = ib.insert(JavaOps.INVOKE.create(mtyMethodType)
                                                .insn(returnClass, paramClasses),
                                        "mty");
                                Var importAsHandle = ib.insert(JavaOps.INVOKE.create(evGetAsFunc)
                                                .insn(importVal, mty),
                                        "handle");
                                ib.insert(
                                        JavaOps.PUT_FIELD.create(field).insn(IRUtils.getThis(ib), importAsHandle)
                                                .assignTo()
                                );
                            }
                        }
                    })
                    .setModifyFuncConvention(convention -> new FunctionConvention() {
                        @Override
                        public void emitCall(IRBuilder ib, Effect effect) {
                            convention.emitCall(ib, effect);
                        }

                        @Override
                        public void emitFuncRef(IRBuilder ib, Effect effect) {
                            convention.emitFuncRef(ib, effect);
                        }

                        @Override
                        public void export(ExportNode node, io.github.eutro.wasm2j.ssa.Module module, JavaExts.JavaClass jClass) {
                            convention.export(node, module, jClass);
                            Map<String, ValueGetter> exports = module.getExtOrRun(EXPORTS_EXT, module, md -> {
                                md.attachExt(EXPORTS_EXT, new HashMap<>());
                                return null;
                            });
                            exports.put(node.name, ib -> {
                                Var exportVar = ib.func.newVar("func");
                                emitFuncRef(ib,
                                        WasmOps.FUNC_REF.create(node.index).insn()
                                                .assignTo(exportVar)
                                );
                                return ib.insert(JavaOps.INVOKE.create(
                                        new JavaExts.JavaMethod(
                                                EV_CLASS,
                                                "func",
                                                Type.getMethodDescriptor(
                                                        Type.getType(ExternVal.class),
                                                        Type.getType(MethodHandle.class)
                                                ),
                                                JavaExts.JavaMethod.Kind.STATIC
                                        )
                                ).insn(exportVar), "export");
                            });
                        }
                    })
                    .build()))
            .then((InPlaceIRPass<io.github.eutro.wasm2j.ssa.Module>) module -> {
                JavaExts.JavaClass jClass = module.getExtOrThrow(JavaExts.JAVA_CLASS);
                Function getExport = new Function();
                JavaExts.JavaMethod method = new JavaExts.JavaMethod(
                        jClass,
                        "getExport",
                        Type.getMethodDescriptor(
                                Type.getType(ExternVal.class),
                                Type.getType(String.class)
                        ),
                        JavaExts.JavaMethod.Kind.VIRTUAL
                );
                getExport.attachExt(JavaExts.FUNCTION_OWNER, jClass);
                getExport.attachExt(JavaExts.FUNCTION_METHOD, method);
                module.functions.add(getExport);
                method.attachExt(JavaExts.METHOD_IMPL, getExport);
                jClass.methods.add(method);

                IRBuilder ib = new IRBuilder(getExport, getExport.newBb());
                module.getExt(EXPORTS_EXT).ifPresent(exports -> {
                    Map<Integer, List<Map.Entry<String, ValueGetter>>> hashes = new TreeMap<>();
                    for (Map.Entry<String, ValueGetter> entry : exports.entrySet()) {
                        hashes.computeIfAbsent(entry.getKey().hashCode(), $ -> new ArrayList<>())
                                .add(entry);
                    }

                    List<BasicBlock> targets = new ArrayList<>();

                    JavaExts.JavaClass stringClass = new JavaExts.JavaClass(Type.getInternalName(String.class));
                    JavaExts.JavaMethod stringEquals = new JavaExts.JavaMethod(
                            stringClass,
                            "equals",
                            "(Ljava/lang/Object;)Z",
                            JavaExts.JavaMethod.Kind.VIRTUAL
                    );

                    Var name = ib.insert(CommonOps.ARG.create(0).insn(), "name");
                    BasicBlock startBlock = ib.getBlock();
                    BasicBlock endBlock = ib.func.newBb();
                    for (List<Map.Entry<String, ValueGetter>> entries : hashes.values()) {
                        ib.setBlock(ib.func.newBb());
                        targets.add(ib.getBlock());

                        BasicBlock k = ib.getBlock();
                        Iterator<Map.Entry<String, ValueGetter>> it = entries.iterator();
                        while (it.hasNext()) {
                            ib.setBlock(k);
                            Map.Entry<String, ValueGetter> entry = it.next();
                            if (it.hasNext()) {
                                k = ib.func.newBb();
                            } else {
                                k = endBlock;
                            }
                            Var isKey = ib.insert(JavaOps.INVOKE.create(stringEquals).insn(
                                    ib.insert(CommonOps.CONST.create(entry.getKey()).insn(), "key"),
                                    name
                            ), "isKey");

                            BasicBlock isKeyBlock = ib.func.newBb();
                            ib.insertCtrl(JavaOps.BR_COND
                                    .create(JavaOps.JumpType.IFEQ).insn(isKey)
                                    .jumpsTo(k, isKeyBlock));
                            ib.setBlock(isKeyBlock);
                            ib.insertCtrl(CommonOps.RETURN.insn(entry.getValue().get(ib)).jumpsTo());
                        }
                    }

                    ib.setBlock(startBlock);
                    Var hash = ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                            stringClass,
                            "hashCode",
                            "()I",
                            JavaExts.JavaMethod.Kind.VIRTUAL
                    )).insn(name), "hash");
                    targets.add(endBlock);
                    ib.insertCtrl(JavaOps.LOOKUPSWITCH
                            .create(hashes.keySet().stream().mapToInt(x -> x).toArray())
                            .insn(hash)
                            .jumpsTo(targets.toArray(new BasicBlock[0])));
                    ib.setBlock(endBlock);
                });
                ib.insertCtrl(CommonOps.RETURN.insn(
                        ib.insert(CommonOps.CONST.create(null).insn(), "null")
                ).jumpsTo());
            })
            .then(ForPass.liftFunctions(Passes.SSA_OPTS))
            .then(ForPass.liftFunctions(Passes.JAVA_PREEMIT))
            .then(JirToJava.INSTANCE)
            .then(CheckJava.INSTANCE);

    public ModuleInst moduleInstantiate(Store store, Module module, ExternVal[] imports) {
        module.validate();

        {
            List<ModuleImport> importVals = moduleImports(module);
            if (importVals.size() != imports.length) {
                throw new IllegalArgumentException(String.format("Import lengths mismatch, got: %d, expected: %s",
                        imports.length,
                        importVals));
            }
            for (int i = 0; i < imports.length; i++) {
                if (importVals.get(i).type != imports[i].getType()) {
                    throw new IllegalArgumentException(String.format("Import types mismatch, got: %s, expected: %s",
                            Arrays.stream(imports)
                                    .map(ExternVal::getType)
                                    .map(Objects::toString)
                                    .collect(Collectors.joining(", ", "[", "]")),
                            importVals
                    ));
                }
            }
        }

        ClassNode classNode = PASS.run(module.getNode());
        classNode.interfaces.add(Type.getInternalName(ModuleInst.class));
        Class<?> moduleClass = store.defineClass(classNode, debugOutput);
        Constructor<?> ctor = moduleClass.getConstructors()[0];

        Object inst;
        try {
            inst = ctor.newInstance((Object[]) imports);
        } catch (InvocationTargetException ite) {
            throw new RuntimeException("Error instantiating module", ite.getCause());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Internal error instantiating module. This is a bug.", e);
        }
        return (ModuleInst) inst;
    }

    public List<ModuleImport> moduleImports(Module module) {
        module.validate();
        if (module.getNode().imports == null) return Collections.emptyList();
        List<ModuleImport> ims = new ArrayList<>();
        for (AbstractImportNode iNode : module.getNode().imports) {
            ims.add(new ModuleImport(
                    iNode.module,
                    iNode.name,
                    ExternType.fromByte(iNode.importType())
            ));
        }
        return ims;
    }

    public List<ModuleExport> moduleExports(Module module) {
        module.validate();
        if (module.getNode().exports == null) return Collections.emptyList();
        List<ModuleExport> exs = new ArrayList<>();
        for (ExportNode export : module.getNode().exports) {
            exs.add(new ModuleExport(
                    export.name,
                    ExternType.fromByte(export.type)
            ));
        }
        return exs;
    }

    public ExternVal instanceExport(ModuleInst inst, String name) {
        return inst.getExport(name);
    }
}

package io.github.eutro.wasm2j.embed.internal;

import io.github.eutro.jwasm.tree.*;
import io.github.eutro.wasm2j.conf.Conventions;
import io.github.eutro.wasm2j.conf.Getters;
import io.github.eutro.wasm2j.conf.api.*;
import io.github.eutro.wasm2j.conf.impl.BasicCallingConvention;
import io.github.eutro.wasm2j.conf.impl.InstanceFunctionConvention;
import io.github.eutro.wasm2j.embed.*;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ext.WasmExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.Op;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.passes.Passes;
import io.github.eutro.wasm2j.passes.convert.Handlify;
import io.github.eutro.wasm2j.passes.convert.JirToJava;
import io.github.eutro.wasm2j.passes.convert.WasmToWir;
import io.github.eutro.wasm2j.passes.convert.WirToJir;
import io.github.eutro.wasm2j.passes.misc.ForPass;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.util.IRUtils;
import io.github.eutro.wasm2j.util.Pair;
import io.github.eutro.wasm2j.util.ValueGetter;
import io.github.eutro.wasm2j.util.ValueGetterSetter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static io.github.eutro.jwasm.Opcodes.MUT_CONST;
import static io.github.eutro.wasm2j.conf.api.ExportableConvention.mangle;
import static io.github.eutro.wasm2j.embed.WebAssembly.EV_CLASS;

public class WasmConvertPass {
    private static final Ext<Map<String, ValueGetter>> EXPORTS_EXT = Ext.create(Map.class);
    private static final Ext<AtomicInteger> IMPORT_COUNTER_EXT = Ext.create(AtomicInteger.class);
    private static final JavaExts.JavaMethod MH_BIND_TO = JavaExts.JavaMethod.fromJava(
            MethodHandle.class,
            "bindTo",
            Object.class
    );
    public static final Type EV_TYPE = Type.getType(ExternVal.class);
    public static final JavaExts.JavaMethod EV_GET_AS_FUNC = new JavaExts.JavaMethod(EV_CLASS, "getAsFunc",
            Type.getMethodDescriptor(
                    Type.getType(MethodHandle.class),
                    Type.getType(MethodType.class)
            ),
            JavaExts.JavaMethod.Kind.VIRTUAL);
    private static final JavaExts.JavaMethod ENUM_ORDINAL = JavaExts.JavaMethod.fromJava(Enum.class, "ordinal");

    public static IRPass<ModuleNode, ClassNode> getPass() {
        AtomicInteger counter = new AtomicInteger(0);
        return WasmToWir.INSTANCE
                .then(new WirToJir(Conventions.createBuilder()
                        .setNameSupplier(() -> {
                            String thisClassName = WebAssembly.class.getName();
                            return thisClassName
                                    .substring(0, thisClassName.lastIndexOf('.'))
                                    .replace('.', '/')
                                    + "/Module" + counter.getAndIncrement();
                        })
                        .setModifyFuncConvention((convention, fn, idx) -> new EmbedFunctionConvention(convention))
                        .setModifyTableConvention((convention, table, idx) -> new EmbedTableConvention(convention, idx))
                        .setModifyGlobalConvention(EmbedGlobalConvention::new)
                        .setModifyMemConvention((convention, table, idx) -> new EmbedMemConvention(convention, idx))
                        .setFunctionImports(WasmConvertPass::createFunctionImport)
                        .setTableImports(WasmConvertPass::createTableImport)
                        .setGlobalImports(WasmConvertPass::createGlobalImport)
                        .setMemoryImports(WasmConvertPass::createMemoryImport)
                        .build()))
                .then((InPlaceIRPass<Module>) module -> {
                    JavaExts.JavaClass jClass = module.getExtOrThrow(JavaExts.JAVA_CLASS);
                    Function getExport = new Function();
                    JavaExts.JavaMethod method = new JavaExts.JavaMethod(
                            jClass,
                            "getExport",
                            Type.getMethodDescriptor(
                                    EV_TYPE,
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
                                        ib.insert(CommonOps.constant(entry.getKey()), "key"),
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
                            ib.insert(CommonOps.constant(null), "null")
                    ).jumpsTo());
                })
                .then(ForPass.liftFunctions(Passes.SSA_OPTS))
                .then(ForPass.liftFunctions(Passes.JAVA_PREEMIT))
                .then(JirToJava.INSTANCE)
                //.then(CheckJava.INSTANCE)
                ;
    }

    static ValueGetter effectHandle(
            String name,
            Module module,
            JavaExts.JavaClass jClass,
            int @Nullable [] permute,
            String desc,
            Op op,
            BiConsumer<IRBuilder, Effect> f
    ) {
        JavaExts.JavaMethod method = new JavaExts.JavaMethod(
                jClass,
                name,
                desc,
                JavaExts.JavaMethod.Kind.FINAL
        );
        Function impl = new Function();
        method.attachExt(JavaExts.METHOD_IMPL, impl);
        impl.attachExt(JavaExts.FUNCTION_OWNER, jClass);
        impl.attachExt(JavaExts.FUNCTION_METHOD, method);

        {
            Type mTy = Type.getMethodType(desc);
            IRBuilder ib = new IRBuilder(impl, impl.newBb());
            List<Var> args = new ArrayList<>();
            Type[] argTys = mTy.getArgumentTypes();
            for (int i = 0; i < argTys.length; i++) {
                args.add(ib.insert(
                        CommonOps.ARG.create(permute == null ? i : permute[i]).insn(),
                        "arg" + i));
            }
            Var[] rets = mTy.getReturnType().getSize() == 0
                    ? new Var[0]
                    : new Var[]{impl.newVar("ret")};
            f.accept(ib, op.insn(args).assignTo(rets));
            ib.insertCtrl(CommonOps.RETURN.insn(rets).jumpsTo());
        }

        Function maybeHandleFn = new Handlify().run(impl);
        if (maybeHandleFn != null) {
            return ib -> ib.insert(new Inliner(ib).inline(maybeHandleFn, Collections.emptyList()), "handle");
        } else { // bad ending :(
            jClass.methods.add(method);
            module.functions.add(impl);
            return ib -> ib.insert(JavaOps.INVOKE
                            .create(MH_BIND_TO)
                            .insn(
                                    ib.insert(JavaOps.HANDLE_OF.create(method).insn(), "f"),
                                    IRUtils.getThis(ib)
                            ),
                    "handle");
        }
    }

    private static Map<String, ValueGetter> getOrMakeExports(Module module) {
        return module.getExtOrRun(EXPORTS_EXT, module, md -> {
            md.attachExt(EXPORTS_EXT, new HashMap<>());
            return null;
        });
    }

    private static AtomicInteger getOrMakeImportC(Module module) {
        return module.getExtOrRun(IMPORT_COUNTER_EXT, module, md -> {
            md.attachExt(IMPORT_COUNTER_EXT, new AtomicInteger());
            return null;
        });
    }

    private static FunctionConvention createFunctionImport(Module module, FuncImportNode importNode, JavaExts.JavaClass jClass, int idx) {
        ModuleNode node = module.getExtOrThrow(WasmExts.MODULE);
        TypeNode type = Objects.requireNonNull(Objects.requireNonNull(node.types).types).get(importNode.type);

        int importIdx = getOrMakeImportC(module).getAndIncrement();
        JavaExts.JavaField field = new JavaExts.JavaField(jClass,
                mangle("f" + importIdx + '_' + importNode.module + '_' + importNode.name),
                Type.getDescriptor(MethodHandle.class),
                false);
        jClass.fields.add(field);
        field.otherAccess |= Opcodes.ACC_FINAL;
        CallingConvention cc = Conventions.DEFAULT_CC; // FIXME get the correct CC
        ValueGetter getHandle = Getters.fieldGetter(
                Getters.GET_THIS,
                field
        );
        Type descTy = cc.getDescriptor(type);
        return new InstanceFunctionConvention(
                ExportableConvention.fieldExporter(field),
                getHandle,
                new JavaExts.JavaMethod(
                        IRUtils.METHOD_HANDLE_CLASS,
                        "invokeExact",
                        descTy.getDescriptor(),
                        JavaExts.JavaMethod.Kind.VIRTUAL
                ),
                cc
        ) {
            // don't just try to bind invokeExact...
            @Override
            public void emitFuncRef(IRBuilder ib, Effect effect) {
                ib.insert(CommonOps.IDENTITY.insn(getHandle.get(ib)).copyFrom(effect));
            }

            @Override
            public void modifyConstructor(IRBuilder ib, JavaExts.JavaMethod ctorMethod, Module module, JavaExts.JavaClass jClass) {
                List<Type> params = ctorMethod.getParamTys();
                params.add(EV_TYPE);
                Var importVal = ib.insert(CommonOps.ARG.create(importIdx).insn(), "import");
                Var returnClass = ib.insert(IRUtils.loadClass(descTy.getReturnType()), "retTy");

                Type[] argTys = descTy.getArgumentTypes();
                Var paramClasses = ib.insert(JavaOps.insns(new TypeInsnNode(
                                        Opcodes.ANEWARRAY,
                                        Type.getInternalName(Class.class)
                                ))
                                .insn(ib.insert(CommonOps.constant(argTys.length), "len")),
                        "paramTys");
                for (int j = 0; j < argTys.length; j++) {
                    ib.insert(JavaOps.ARRAY_SET.create().insn(
                            paramClasses,
                            ib.insert(CommonOps.constant(j), "i"),
                            ib.insert(IRUtils.loadClass(argTys[j]), "pty")
                    ).assignTo());
                }

                Var mty = ib.insert(JavaOps.INVOKE.create(IRUtils.MTY_METHOD_TYPE)
                                .insn(returnClass, paramClasses),
                        "mty");
                Var importAsHandle = ib.insert(JavaOps.INVOKE.create(EV_GET_AS_FUNC)
                                .insn(importVal, mty),
                        "handle");
                ib.insert(
                        JavaOps.PUT_FIELD.create(field).insn(IRUtils.getThis(ib), importAsHandle)
                                .assignTo()
                );
            }

            @Override
            public void export(ExportNode node, Module module, JavaExts.JavaClass jClass) {
                super.export(node, module, jClass);
                getOrMakeExports(module).put(node.name, ib ->
                        ib.insert(JavaOps.INVOKE
                                        .create(JavaExts.JavaMethod.fromJava(ExternVal.class, "func", MethodHandle.class))
                                        .insn(getHandle.get(ib)),
                                "export"));
            }
        };
    }

    private static TableConvention createTableImport(Module module, TableImportNode importNode, JavaExts.JavaClass jClass, int idx) {
        int importIdx = getOrMakeImportC(module).getAndIncrement();
        JavaExts.JavaField field = new JavaExts.JavaField(jClass,
                mangle("t" + importIdx + '_' + importNode.module + '_' + importNode.name),
                Type.getDescriptor(Table.class),
                false
        );
        jClass.fields.add(field);
        ValueGetterSetter table = Getters.fieldGetter(Getters.GET_THIS, field);
        JavaExts.JavaMethod get = JavaExts.JavaMethod.fromJava(Table.class, "get", int.class);
        JavaExts.JavaMethod set = JavaExts.JavaMethod.fromJava(Table.class, "set", int.class, Object.class);
        JavaExts.JavaMethod size = JavaExts.JavaMethod.fromJava(Table.class, "size");
        JavaExts.JavaMethod grow = JavaExts.JavaMethod.fromJava(Table.class, "grow", int.class, Object.class);
        return new TableConvention() {
            @Override
            public void emitTableRef(IRBuilder ib, Effect effect) {
                ib.insert(JavaOps.INVOKE
                        .create(get)
                        .insn(table.get(ib), effect.insn().args.get(0))
                        .copyFrom(effect));
            }

            @Override
            public void emitTableStore(IRBuilder ib, Effect effect) {
                ib.insert(JavaOps.INVOKE
                        .create(set)
                        .insn(table.get(ib),
                                effect.insn().args.get(1),
                                effect.insn().args.get(0))
                        .copyFrom(effect));
            }

            @Override
            public void emitTableSize(IRBuilder ib, Effect effect) {
                ib.insert(JavaOps.INVOKE
                        .create(size)
                        .insn(table.get(ib))
                        .copyFrom(effect));
            }

            @Override
            public void emitTableGrow(IRBuilder ib, Effect effect) {
                ib.insert(JavaOps.INVOKE
                        .create(grow)
                        .insn(table.get(ib),
                                effect.insn().args.get(0),
                                effect.insn().args.get(1))
                        .copyFrom(effect));
            }

            @Override
            public void modifyConstructor(IRBuilder ib, JavaExts.JavaMethod ctorMethod, Module module, JavaExts.JavaClass jClass) {
                JavaExts.JavaMethod getAsTable = JavaExts.JavaMethod.fromJava(ExternVal.class, "getAsTable");
                List<Type> params = ctorMethod.getParamTys();
                params.add(EV_TYPE);
                ib.insert(JavaOps.PUT_FIELD
                        .create(field)
                        .insn(IRUtils.getThis(ib),
                                ib.insert(JavaOps.INVOKE
                                                .create(getAsTable)
                                                .insn(ib.insert(CommonOps.ARG.create(importIdx).insn(),
                                                        "extern")),
                                        "table"))
                        .assignTo());
            }

            @Override
            public void export(ExportNode node, Module module, JavaExts.JavaClass jClass) {
                ExportableConvention.fieldExporter(field).export(node, module, jClass);
                getOrMakeExports(module).put(node.name, ib ->
                        ib.insert(JavaOps.INVOKE
                                        .create(JavaExts.JavaMethod.fromJava(ExternVal.class, "table", Table.class))
                                        .insn(table.get(ib)),
                                "export"));
            }
        };
    }

    private static GlobalConvention createGlobalImport(Module module, GlobalImportNode importNode, JavaExts.JavaClass jClass, int idx) {
        int importIdx = getOrMakeImportC(module).getAndIncrement();
        JavaExts.JavaField field = new JavaExts.JavaField(jClass,
                mangle("g" + importIdx + '_' + importNode.module + '_' + importNode.name),
                Type.getDescriptor(Global.class),
                false);
        jClass.fields.add(field);
        ValueGetterSetter global = Getters.fieldGetter(Getters.GET_THIS, field);
        JavaExts.JavaMethod get = JavaExts.JavaMethod.fromJava(Global.class, "get");
        JavaExts.JavaMethod set = JavaExts.JavaMethod.fromJava(Global.class, "set", Object.class);
        Type objTy = Type.getType(Object.class);
        return new GlobalConvention() {
            @Override
            public void emitGlobalRef(IRBuilder ib, Effect effect) {
                ib.insert(CommonOps.IDENTITY
                        .insn(BasicCallingConvention.unboxed(ib,
                                ib.insert(JavaOps.INVOKE.create(get).insn(global.get(ib)), "boxed"),
                                objTy,
                                importNode.type.type
                        ))
                        .copyFrom(effect));
            }

            @Override
            public void emitGlobalStore(IRBuilder ib, Effect effect) {
                ib.insert(JavaOps.INVOKE
                        .create(set)
                        .insn(global.get(ib),
                                BasicCallingConvention.maybeBoxed(ib,
                                        effect.insn().args.get(0),
                                        importNode.type.type,
                                        objTy))
                        .assignTo());
            }

            @Override
            public void modifyConstructor(IRBuilder ib, JavaExts.JavaMethod ctorMethod, Module module, JavaExts.JavaClass jClass) {
                JavaExts.JavaMethod getAsGlobal = JavaExts.JavaMethod.fromJava(ExternVal.class, "getAsGlobal");
                List<Type> params = ctorMethod.getParamTys();
                params.add(EV_TYPE);
                ib.insert(JavaOps.PUT_FIELD
                        .create(field)
                        .insn(IRUtils.getThis(ib),
                                ib.insert(JavaOps.INVOKE
                                                .create(getAsGlobal)
                                                .insn(ib.insert(CommonOps.ARG.create(importIdx).insn(),
                                                        "extern")),
                                        "global"))
                        .assignTo());
            }

            @Override
            public void export(ExportNode node, Module module, JavaExts.JavaClass jClass) {
                ExportableConvention.fieldExporter(field).export(node, module, jClass);
                getOrMakeExports(module).put(node.name, ib ->
                        ib.insert(JavaOps.INVOKE
                                        .create(JavaExts.JavaMethod.fromJava(ExternVal.class, "global", Global.class))
                                        .insn(global.get(ib)),
                                "export"));
            }
        };
    }

    private static MemoryConvention createMemoryImport(Module module, MemImportNode importNode, JavaExts.JavaClass jClass, int idx) {
        int importIdx = getOrMakeImportC(module).getAndIncrement();
        JavaExts.JavaField field = new JavaExts.JavaField(jClass,
                mangle("m" + importIdx + '_' + importNode.module + '_' + importNode.name),
                Type.getDescriptor(Memory.class),
                false
        );
        jClass.fields.add(field);
        ValueGetterSetter memory = Getters.fieldGetter(Getters.GET_THIS, field);
        JavaExts.JavaMethod size = JavaExts.JavaMethod.fromJava(Memory.class, "size");
        JavaExts.JavaMethod grow = JavaExts.JavaMethod.fromJava(Memory.class, "grow", int.class);
        JavaExts.JavaMethod init = JavaExts.JavaMethod.fromJava(Memory.class, "init",
                int.class, int.class, int.class, ByteBuffer.class);
        return new MemoryConvention() {
            @Override
            public void emitMemLoad(IRBuilder ib, Effect effect) {
                WasmOps.WithMemArg<WasmOps.DerefType> arg = WasmOps.MEM_LOAD.cast(effect.insn().op).arg;
                ib.insert(JavaOps.insns(new InvokeDynamicInsnNode("load",
                                Type.getMethodDescriptor(BasicCallingConvention.javaType(arg.value.outType),
                                        Type.getType(Memory.class),
                                        Type.INT_TYPE),
                                Memory.Bootstrap.BOOTSTRAP_HANDLE,
                                Memory.LoadMode.fromOpcode(arg.value.getOpcode())))
                        .insn(memory.get(ib), getAddr(ib, arg, effect.insn().args.get(0)))
                        .copyFrom(effect));
            }

            @Override
            public void emitMemStore(IRBuilder ib, Effect effect) {
                WasmOps.WithMemArg<WasmOps.StoreType> arg = WasmOps.MEM_STORE.cast(effect.insn().op).arg;
                ib.insert(JavaOps.insns(new InvokeDynamicInsnNode("store",
                                Type.getMethodDescriptor(Type.VOID_TYPE,
                                        Type.getType(Memory.class),
                                        Type.INT_TYPE,
                                        BasicCallingConvention.javaType(arg.value.getType())),
                                Memory.Bootstrap.BOOTSTRAP_HANDLE,
                                Memory.StoreMode.fromOpcode(arg.value.getOpcode())))
                        .insn(memory.get(ib),
                                getAddr(ib, arg, effect.insn().args.get(0)),
                                effect.insn().args.get(1))
                        .copyFrom(effect));
            }

            private Var getAddr(IRBuilder ib, WasmOps.WithMemArg<?> arg, Var addr) {
                if (arg.offset != 0) {
                    return ib.insert(JavaOps.insns(new InsnNode(Opcodes.IADD))
                                    .insn(addr, ib.insert(CommonOps.constant(arg.offset), "offset")),
                            "addr");
                }
                return addr;
            }

            @Override
            public void emitMemSize(IRBuilder ib, Effect effect) {
                ib.insert(JavaOps.INVOKE.create(size).insn(memory.get(ib)).copyFrom(effect));
            }

            @Override
            public void emitMemGrow(IRBuilder ib, Effect effect) {
                ib.insert(JavaOps.INVOKE.create(grow)
                        .insn(memory.get(ib), effect.insn().args.get(0)).copyFrom(effect));
            }

            @Override
            public void emitMemInit(IRBuilder ib, Effect effect, Var data) {
                List<Var> args = new ArrayList<>();
                args.add(memory.get(ib));
                args.addAll(effect.insn().args);
                args.add(data);
                ib.insert(JavaOps.INVOKE.create(init).insn(args).copyFrom(effect));
            }

            @Override
            public void modifyConstructor(IRBuilder ib, JavaExts.JavaMethod ctorMethod, Module module, JavaExts.JavaClass jClass) {
                JavaExts.JavaMethod getAsMemory = JavaExts.JavaMethod.fromJava(ExternVal.class, "getAsMemory");
                List<Type> params = ctorMethod.getParamTys();
                params.add(EV_TYPE);
                ib.insert(JavaOps.PUT_FIELD
                        .create(field)
                        .insn(IRUtils.getThis(ib),
                                ib.insert(JavaOps.INVOKE
                                                .create(getAsMemory)
                                                .insn(ib.insert(CommonOps.ARG.create(importIdx).insn(),
                                                        "extern")),
                                        "memory"))
                        .assignTo());
            }

            @Override
            public void export(ExportNode node, Module module, JavaExts.JavaClass jClass) {
                ExportableConvention.fieldExporter(field).export(node, module, jClass);
                getOrMakeExports(module).put(node.name, ib ->
                        ib.insert(JavaOps.INVOKE
                                        .create(JavaExts.JavaMethod.fromJava(ExternVal.class, "memory", Memory.class))
                                        .insn(memory.get(ib)),
                                "export"));
            }
        };
    }

    private static class EmbedFunctionConvention extends FunctionConvention.Delegating {
        public EmbedFunctionConvention(FunctionConvention convention) {
            super(convention);
        }

        @Override
        public void export(ExportNode node, Module module, JavaExts.JavaClass jClass) {
            delegate.export(node, module, jClass);
            getOrMakeExports(module).put(node.name, ib -> {
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
                                        EV_TYPE,
                                        Type.getType(MethodHandle.class)
                                ),
                                JavaExts.JavaMethod.Kind.STATIC
                        )
                ).insn(exportVar), "export");
            });
        }
    }

    private static class EmbedTableConvention extends TableConvention.Delegating {
        private final int idx;
        ValueGetter getHandle, setHandle, sizeHandle, growHandle;
        private boolean exported = false;

        public EmbedTableConvention(TableConvention convention, int idx) {
            super(convention);
            this.idx = idx;
        }

        @Override
        public void modifyConstructor(IRBuilder ib, JavaExts.JavaMethod ctorMethod, Module module, JavaExts.JavaClass jClass) {
            super.modifyConstructor(ib, ctorMethod, module, jClass);
            if (exported) {
                getHandle = effectHandle(
                        "table" + idx + "$get",
                        module,
                        jClass,
                        null,
                        "(I)Ljava/lang/Object;",
                        WasmOps.TABLE_REF.create(idx),
                        delegate::emitTableRef
                );
                setHandle = effectHandle(
                        "table" + idx + "$set",
                        module,
                        jClass,
                        new int[]{1, 0},
                        "(ILjava/lang/Object;)V",
                        WasmOps.TABLE_STORE.create(idx),
                        delegate::emitTableStore
                );
                sizeHandle = effectHandle(
                        "table" + idx + "$size",
                        module,
                        jClass,
                        null,
                        "()I",
                        WasmOps.TABLE_SIZE.create(idx),
                        delegate::emitTableSize
                );
                growHandle = effectHandle(
                        "table" + idx + "$grow",
                        module,
                        jClass,
                        null,
                        "(ILjava/lang/Object;)I",
                        WasmOps.TABLE_GROW.create(idx),
                        delegate::emitTableGrow
                );
            }
        }

        @Override
        public void export(ExportNode node, Module module, JavaExts.JavaClass jClass) {
            super.export(node, module, jClass);
            exported = true;
            getOrMakeExports(module).put(node.name, ib -> ib.insert(JavaOps.INVOKE
                            .create(JavaExts.JavaMethod.fromJava(
                                    ExternVal.class,
                                    "table",
                                    Table.class
                            ))
                            .insn(ib.insert(JavaOps.INVOKE
                                            .create(JavaExts.JavaMethod.fromJava(
                                                    Table.HandleTable.class,
                                                    "create",
                                                    MethodHandle.class,
                                                    MethodHandle.class,
                                                    MethodHandle.class,
                                                    MethodHandle.class
                                            ))
                                            .insn(
                                                    getHandle.get(ib),
                                                    setHandle.get(ib),
                                                    sizeHandle.get(ib),
                                                    growHandle.get(ib)
                                            ),
                                    "table")),
                    "extern"));
        }
    }

    private static class EmbedGlobalConvention extends GlobalConvention.Delegating implements GlobalConvention {
        private final GlobalTypeNode type;
        private final int idx;
        ValueGetter getHandle, setHandle;
        private boolean exported;

        public EmbedGlobalConvention(GlobalConvention convention, GlobalNode global, int idx) {
            this(convention, global.type, idx);
        }

        public EmbedGlobalConvention(GlobalConvention convention, GlobalTypeNode type, int idx) {
            super(convention);
            this.type = type;
            this.idx = idx;
        }

        @Override
        public void modifyConstructor(IRBuilder ib, JavaExts.JavaMethod ctorMethod, Module module, JavaExts.JavaClass jClass) {
            super.modifyConstructor(ib, ctorMethod, module, jClass);
            if (exported) {
                Type jTy = BasicCallingConvention.javaType(type.type);
                getHandle = effectHandle(
                        "global" + idx + "$get",
                        module,
                        jClass,
                        null,
                        Type.getMethodDescriptor(jTy),
                        WasmOps.GLOBAL_REF.create(idx),
                        delegate::emitGlobalRef
                );
                setHandle = type.type != MUT_CONST ?
                        effectHandle(
                                "global" + idx + "$set",
                                module,
                                jClass,
                                null,
                                Type.getMethodDescriptor(Type.VOID_TYPE, jTy),
                                WasmOps.GLOBAL_SET.create(idx),
                                delegate::emitGlobalStore
                        )
                        : jb -> jb.insert(CommonOps.constant(null), "null");
            }
        }

        @Override
        public void export(ExportNode node, Module module, JavaExts.JavaClass jClass) {
            super.export(node, module, jClass);
            exported = true;
            getOrMakeExports(module).put(node.name, ib -> ib.insert(JavaOps.INVOKE
                            .create(JavaExts.JavaMethod.fromJava(
                                    ExternVal.class,
                                    "global",
                                    Global.class
                            ))
                            .insn(ib.insert(JavaOps.INVOKE
                                            .create(JavaExts.JavaMethod.fromJava(
                                                    Global.HandleGlobal.class,
                                                    "create",
                                                    MethodHandle.class,
                                                    MethodHandle.class
                                            ))
                                            .insn(
                                                    getHandle.get(ib),
                                                    setHandle.get(ib)
                                            ),
                                    "global")),
                    "extern"));
        }
    }

    private static class EmbedMemConvention extends MemoryConvention.Delegating {
        private final int idx;
        ValueGetter getHandle, storeHandle, sizeHandle, growHandle, initHandle;
        private boolean exported;

        public EmbedMemConvention(MemoryConvention convention, int idx) {
            super(convention);
            this.idx = idx;
        }

        @Override
        public void modifyConstructor(IRBuilder ctorIb, JavaExts.JavaMethod ctorMethod, Module module, JavaExts.JavaClass jClass) {
            super.modifyConstructor(ctorIb, ctorMethod, module, jClass);
            if (exported) {
                getHandle = effectHandle(
                        "mem" + idx + "$get",
                        module,
                        jClass,
                        null,
                        Type.getMethodDescriptor(
                                Type.getType(MethodHandle.class),
                                Type.getType(Memory.LoadMode.class)),
                        CommonOps.IDENTITY,
                        (ib, fx) -> {
                            Memory.LoadMode[] values = Memory.LoadMode.values();
                            List<BasicBlock> targets = setupTargets(ib, values);
                            BasicBlock failBb = ib.getBlock();

                            for (int i = 0; i < values.length; i++) {
                                Memory.LoadMode loadMode = values[i];
                                WasmOps.DerefType derefTy = WasmOps.DerefType.fromOpcode(loadMode.opcode);
                                ValueGetter handle = effectHandle(
                                        "mem" + idx + "$get$" + loadMode.toString().toLowerCase(Locale.ROOT),
                                        module,
                                        jClass,
                                        null,
                                        Type.getMethodDescriptor(
                                                BasicCallingConvention.javaType(derefTy.outType),
                                                Type.INT_TYPE
                                        ),
                                        WasmOps.MEM_LOAD.create(new WasmOps.WithMemArg<>(derefTy, 0)),
                                        delegate::emitMemLoad
                                );
                                ib.setBlock(targets.get(i));
                                ib.insertCtrl(CommonOps.RETURN.insn(handle.get(ib)).jumpsTo());
                            }
                            ib.setBlock(failBb);
                            ib.insert(CommonOps.constant(null).copyFrom(fx));
                        }
                );
                storeHandle = effectHandle(
                        "mem" + idx + "$store",
                        module,
                        jClass,
                        null,
                        Type.getMethodDescriptor(
                                Type.getType(MethodHandle.class),
                                Type.getType(Memory.LoadMode.class)),
                        CommonOps.IDENTITY,
                        (ib, fx) -> {
                            Memory.StoreMode[] values = Memory.StoreMode.values();
                            List<BasicBlock> targets = setupTargets(ib, values);
                            BasicBlock failBb = ib.getBlock();

                            for (int i = 0; i < values.length; i++) {
                                Memory.StoreMode storeMode = values[i];
                                WasmOps.StoreType storeTy = WasmOps.StoreType.fromOpcode(storeMode.opcode());
                                ValueGetter handle = effectHandle(
                                        "mem" + idx + "$store$" + storeMode.toString().toLowerCase(Locale.ROOT),
                                        module,
                                        jClass,
                                        null,
                                        Type.getMethodDescriptor(
                                                Type.VOID_TYPE,
                                                Type.INT_TYPE,
                                                BasicCallingConvention.javaType(storeTy.getType())
                                        ),
                                        WasmOps.MEM_STORE.create(new WasmOps.WithMemArg<>(storeTy, 0)),
                                        delegate::emitMemStore
                                );
                                ib.setBlock(targets.get(i));
                                ib.insertCtrl(CommonOps.RETURN.insn(handle.get(ib)).jumpsTo());
                            }
                            ib.setBlock(failBb);
                            ib.insert(CommonOps.constant(null).copyFrom(fx));
                        }
                );
                sizeHandle = effectHandle(
                        "mem" + idx + "$size",
                        module,
                        jClass,
                        null,
                        "()I",
                        WasmOps.MEM_SIZE.create(0),
                        delegate::emitMemSize
                );
                growHandle = effectHandle(
                        "mem" + idx + "$grow",
                        module,
                        jClass,
                        null,
                        "(I)I",
                        WasmOps.MEM_GROW.create(0),
                        delegate::emitMemGrow
                );
                initHandle = effectHandle(
                        "mem" + idx + "$init",
                        module,
                        jClass,
                        null,
                        "(IIILjava/nio/ByteBuffer;)V",
                        WasmOps.MEM_INIT.create(Pair.of(0, 0)),
                        (ib, fx) -> {
                            Var data = ib.insert(CommonOps.ARG.create(3).insn(), "data");
                            delegate.emitMemInit(ib, fx, data);
                        }
                );
            }
        }

        @NotNull
        private static List<BasicBlock> setupTargets(IRBuilder ib, Memory.HasOpcode[] values) {
            List<BasicBlock> targets = new ArrayList<>();
            for (int i = 0; i < values.length; i++) {
                targets.add(ib.func.newBb());
            }
            BasicBlock failBb = ib.func.newBb();
            targets.add(failBb);
            ib.insertCtrl(JavaOps.TABLESWITCH.create()
                    .insn(ib.insert(JavaOps.INVOKE.create(ENUM_ORDINAL)
                                    .insn(ib.insert(CommonOps.ARG.create(0).insn(), "mode")),
                            "ordinal"))
                    .jumpsTo(targets));
            ib.setBlock(failBb);
            return targets;
        }

        @Override
        public void export(ExportNode node, Module module, JavaExts.JavaClass jClass) {
            super.export(node, module, jClass);
            exported = true;
            getOrMakeExports(module).put(node.name, ib -> ib.insert(JavaOps.INVOKE
                            .create(JavaExts.JavaMethod.fromJava(
                                    ExternVal.class,
                                    "memory",
                                    Memory.class
                            ))
                            .insn(ib.insert(JavaOps.INVOKE
                                            .create(JavaExts.JavaMethod.fromJava(
                                                    Memory.HandleMemory.class,
                                                    "create",
                                                    MethodHandle.class,
                                                    MethodHandle.class,
                                                    MethodHandle.class,
                                                    MethodHandle.class,
                                                    MethodHandle.class
                                            ))
                                            .insn(
                                                    getHandle.get(ib),
                                                    storeHandle.get(ib),
                                                    sizeHandle.get(ib),
                                                    growHandle.get(ib),
                                                    initHandle.get(ib)
                                            ),
                                    "global")),
                    "extern"));
        }
    }
}

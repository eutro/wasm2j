package io.github.eutro.wasm2j.embed.internal;

import io.github.eutro.jwasm.tree.*;
import io.github.eutro.wasm2j.api.WasmCompiler;
import io.github.eutro.wasm2j.api.events.JirPassesEvent;
import io.github.eutro.wasm2j.api.events.ModifyConventionsEvent;
import io.github.eutro.wasm2j.api.support.ExternType;
import io.github.eutro.wasm2j.api.support.NameMangler;
import io.github.eutro.wasm2j.core.conf.Getters;
import io.github.eutro.wasm2j.core.conf.api.*;
import io.github.eutro.wasm2j.core.conf.impl.BasicCallingConvention;
import io.github.eutro.wasm2j.core.conf.impl.InstanceFunctionConvention;
import io.github.eutro.wasm2j.core.ext.Ext;
import io.github.eutro.wasm2j.core.ext.JavaExts;
import io.github.eutro.wasm2j.core.ext.WasmExts;
import io.github.eutro.wasm2j.core.ops.*;
import io.github.eutro.wasm2j.core.passes.IRPass;
import io.github.eutro.wasm2j.core.passes.convert.Handlify;
import io.github.eutro.wasm2j.core.ssa.Module;
import io.github.eutro.wasm2j.core.ssa.*;
import io.github.eutro.wasm2j.core.util.IRUtils;
import io.github.eutro.wasm2j.core.util.Pair;
import io.github.eutro.wasm2j.core.util.ValueGetter;
import io.github.eutro.wasm2j.core.util.ValueGetterSetter;
import io.github.eutro.wasm2j.embed.*;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static io.github.eutro.jwasm.Opcodes.MUT_CONST;
import static io.github.eutro.wasm2j.core.util.Lazy.lazy;

public class WasmConvertPass {
    private static final Ext<Map<String, ValueGetter>> EXPORTS_EXT = Ext.create(Map.class, "EXPORTS_EXT");
    private static final Ext<AtomicInteger> IMPORT_COUNTER_EXT = Ext.create(AtomicInteger.class, "IMPORT_COUNTER_EXT");
    private static final JClass.JavaMethod MH_BIND_TO = IRUtils.METHOD_HANDLE_CLASS.lookupMethod("bindTo", Object.class);
    public static final Type EV_TYPE = Type.getType(ExternVal.class);
    public static final JClass EV_CLASS = JClass.emptyFromJava(ExternVal.class);
    public static final JClass.JavaMethod EV_GET_AS_FUNC = EV_CLASS.lookupMethod("getAsHandle", MethodType.class);
    private static final JClass.JavaMethod ENUM_ORDINAL = JClass.emptyFromJava(Enum.class).lookupMethod("ordinal");
    private static final JClass STRING_CLASS = JClass.emptyFromJava(String.class);
    private static final JClass.JavaMethod STRING_EQUALS = STRING_CLASS.lookupMethod("equals", Object.class);
    private static final JClass TABLE_CLASS = JClass.emptyFromJava(Table.class);
    private static final JClass GLOBAL_CLASS = JClass.emptyFromJava(Global.class);
    private static final JClass MEMORY_CLASS = JClass.emptyFromJava(Memory.class);

    public static IRPass<ModuleNode, ClassNode> getPass() {
        WasmCompiler cc = new WasmCompiler();

        AtomicInteger counter = new AtomicInteger(0);
        String thisClassName = WebAssembly.class.getName();
        String packageName = thisClassName
                .substring(0, thisClassName.lastIndexOf('.'))
                .replace('.', '/');
        cc.lift().listen(ModifyConventionsEvent.class, evt -> evt.conventionBuilder
                .setNameSupplier(() -> packageName + "/Module" + counter.getAndIncrement())
                .setModifyFuncConvention((convention, fn, idx) -> new EmbedFunctionConvention(convention, fn.left))
                .setModifyTableConvention(EmbedTableConvention::new)
                .setModifyGlobalConvention(EmbedGlobalConvention::new)
                .setModifyMemConvention(EmbedMemConvention::new)
                .setFunctionImports(WasmConvertPass::createFunctionImport)
                .setTableImports(WasmConvertPass::createTableImport)
                .setGlobalImports(WasmConvertPass::createGlobalImport)
                .setMemoryImports(WasmConvertPass::createMemoryImport));

        cc.lift().listen(JirPassesEvent.class, WasmConvertPass::buildExports);

        Queue<ClassNode> classes = cc.outputsAsQueue();
        return node -> {
            cc.submitNode(node).run();
            ClassNode ret = classes.poll();
            if (ret == null) {
                throw new IllegalStateException();
            }
            return ret;
        };
    }

    static ValueGetter effectHandle(
            String name,
            JClass jClass,
            String desc,
            Op op,
            BiConsumer<IRBuilder, Effect> f
    ) {
        Function impl = new Function();
        {
            Type mTy = Type.getMethodType(desc);
            IRBuilder ib = new IRBuilder(impl, impl.newBb());
            List<Var> args = new ArrayList<>();
            Type[] argTys = mTy.getArgumentTypes();
            for (int i = 0; i < argTys.length; i++) {
                args.add(ib.insert(
                        CommonOps.ARG.create(i).insn(),
                        ib.func.newVar("arg", i)));
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
            JClass.JavaMethod method = new JClass.JavaMethod(
                    jClass,
                    name,
                    desc,
                    Opcodes.ACC_PRIVATE
            );
            jClass.methods.add(method);
            impl.attachExt(JavaExts.FUNCTION_METHOD, method);
            method.attachExt(JavaExts.METHOD_IMPL, lazy(() -> impl));
            return ib -> ib.insert(JavaOps.INVOKE
                            .create(MH_BIND_TO)
                            .insn(
                                    ib.insert(JavaOps.HANDLE_OF.create(method).insn(), "f"),
                                    IRUtils.getThis(ib)
                            ),
                    "handle");
        }
    }

    private static Map<String, ValueGetter> getOrMakeExports(JClass jClass) {
        return jClass.getExtOrRun(EXPORTS_EXT, jClass, md -> {
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

    private static String mangle(String name) {
        return NameMangler.jvmUnqualified(NameMangler.IllegalTokenPolicy.MANGLE_BIJECTIVE)
                .mangle(name);
    }

    private static FunctionConvention createFunctionImport(Module module, FuncImportNode importNode, JClass jClass, int idx) {
        ModuleNode node = module.getExtOrThrow(WasmExts.MODULE);
        TypeNode type = Objects.requireNonNull(Objects.requireNonNull(node.types).types).get(importNode.type);

        int importIdx = getOrMakeImportC(module).getAndIncrement();
        JClass.JavaField handleField = new JClass.JavaField(jClass,
                mangle("f" + importIdx + '_' + importNode.module + '_' + importNode.name),
                Type.getDescriptor(MethodHandle.class),
                false);
        jClass.fields.add(handleField);
        handleField.access |= Opcodes.ACC_FINAL;
        CallingConvention cc = BasicCallingConvention.INSTANCE; // FIXME get the correct CC
        ValueGetter getHandle = Getters.fieldGetter(Getters.GET_THIS, handleField);
        Type descTy = cc.getDescriptor(type);
        return new InstanceFunctionConvention(
                ExportableConvention.noop(),
                getHandle,
                new JClass.JavaMethod(
                        IRUtils.METHOD_HANDLE_CLASS,
                        "invokeExact",
                        descTy.getDescriptor(),
                        Opcodes.ACC_PUBLIC
                ),
                cc
        ) {
            // don't just try to bind invokeExact...
            @Override
            public void emitFuncRef(IRBuilder ib, Effect effect) {
                ib.insert(CommonOps.IDENTITY.insn(getHandle.get(ib)).copyFrom(effect));
            }

            @Override
            public void modifyConstructor(IRBuilder ib, JClass.JavaMethod ctorMethod, Module module, JClass jClass) {
                List<Type> params = ctorMethod.getParamTys();
                params.add(EV_TYPE);
                Var importVal = ib.insert(CommonOps.ARG.create(importIdx).insn(), "import");
                Var returnClass = ib.insert(IRUtils.loadClass(descTy.getReturnType()), "retTy");

                Type[] argTys = descTy.getArgumentTypes();
                Var paramClasses = ib.insert(JavaOps.insns(new TypeInsnNode(
                                        Opcodes.ANEWARRAY,
                                        "java/lang/Class"
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
                        JavaOps.PUT_FIELD.create(handleField).insn(IRUtils.getThis(ib), importAsHandle)
                                .assignTo()
                );
            }

            @Override
            public void export(ExportNode node, Module module, JClass jClass) {
                super.export(node, module, jClass);
                getOrMakeExports(jClass).put(node.name, ib -> ib.insert(
                        EmbedFunctionConvention.CREATE_HANDLE_FUNC
                                .insn(ib.insert(EmbedFunctionConvention.PARSE_FUNC_TYPE
                                                        .insn(ib.insert(CommonOps.constant(ExternType.Func.encode(type)),
                                                                "encoded")),
                                                "ty"),
                                        getHandle.get(ib)),
                        "export"));
            }
        };
    }

    private static TableConvention createTableImport(Module module, TableImportNode importNode, JClass jClass, int idx) {
        int importIdx = getOrMakeImportC(module).getAndIncrement();
        JClass.JavaField field = new JClass.JavaField(jClass,
                mangle("t" + importIdx + '_' + importNode.module + '_' + importNode.name),
                Type.getDescriptor(Table.class),
                false
        );
        jClass.fields.add(field);
        ValueGetterSetter table = Getters.fieldGetter(Getters.GET_THIS, field);
        JClass.JavaMethod get = TABLE_CLASS.lookupMethod("get", int.class);
        JClass.JavaMethod set = TABLE_CLASS.lookupMethod("set", int.class, Object.class);
        JClass.JavaMethod size = TABLE_CLASS.lookupMethod("size");
        JClass.JavaMethod grow = TABLE_CLASS.lookupMethod("grow", int.class, Object.class);
        JClass.JavaMethod init = TABLE_CLASS.lookupMethod("init", int.class, int.class, int.class, Object[].class);
        Type componentType = BasicCallingConvention.javaType(importNode.type);
        return new TableConvention.Delegating(null) {
            @Override
            public void emitTableRef(IRBuilder ib, Effect effect) {
                ib.insert(JavaOps.insns(new TypeInsnNode(Opcodes.CHECKCAST, componentType.getInternalName()))
                        .insn(ib.insert(JavaOps.INVOKE
                                        .create(get)
                                        .insn(table.get(ib), effect.insn().args().get(0)),
                                "raw"))
                        .copyFrom(effect));
            }

            @Override
            public void emitTableStore(IRBuilder ib, Effect effect) {
                ib.insert(JavaOps.INVOKE
                        .create(set)
                        .insn(table.get(ib),
                                effect.insn().args().get(0),
                                effect.insn().args().get(1))
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
                                effect.insn().args().get(0),
                                effect.insn().args().get(1))
                        .copyFrom(effect));
            }

            @Override
            public void emitTableInit(IRBuilder ib, Effect effect, Var data) {
                ib.insert(JavaOps.INVOKE
                        .create(init)
                        .insn(table.get(ib),
                                effect.insn().args().get(0),
                                effect.insn().args().get(1),
                                effect.insn().args().get(2),
                                data)
                        .copyFrom(effect));
            }

            @Override
            public void modifyConstructor(IRBuilder ib, JClass.JavaMethod ctorMethod, Module module, JClass jClass) {
                JClass.JavaMethod getAsTable = EV_CLASS.lookupMethod("getAsTable");
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
            public void export(ExportNode node, Module module, JClass jClass) {
                getOrMakeExports(jClass).put(node.name, table);
            }
        };
    }

    private static GlobalConvention createGlobalImport(Module module, GlobalImportNode importNode, JClass jClass, int idx) {
        int importIdx = getOrMakeImportC(module).getAndIncrement();
        JClass.JavaField field = new JClass.JavaField(jClass,
                mangle("g" + importIdx + '_' + importNode.module + '_' + importNode.name),
                Type.getDescriptor(Global.class),
                false);
        jClass.fields.add(field);
        ValueGetterSetter global = Getters.fieldGetter(Getters.GET_THIS, field);
        JClass.JavaMethod get = GLOBAL_CLASS.lookupMethod("get");
        JClass.JavaMethod set = GLOBAL_CLASS.lookupMethod("set", Object.class);
        Type objTy = Type.getType(Object.class);
        return new GlobalConvention.Delegating(null) {
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
                                        effect.insn().args().get(0),
                                        importNode.type.type,
                                        objTy))
                        .assignTo());
            }

            @Override
            public void modifyConstructor(IRBuilder ib, JClass.JavaMethod ctorMethod, Module module, JClass jClass) {
                JClass.JavaMethod getAsGlobal = EV_CLASS.lookupMethod("getAsGlobal");
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
            public void export(ExportNode node, Module module, JClass jClass) {
                getOrMakeExports(jClass).put(node.name, global);
            }
        };
    }

    private static MemoryConvention createMemoryImport(Module module, MemImportNode importNode, JClass jClass, int idx) {
        int importIdx = getOrMakeImportC(module).getAndIncrement();
        JClass.JavaField field = new JClass.JavaField(jClass,
                mangle("m" + importIdx + '_' + importNode.module + '_' + importNode.name),
                Type.getDescriptor(Memory.class),
                false
        );
        jClass.fields.add(field);
        ValueGetterSetter memory = Getters.fieldGetter(Getters.GET_THIS, field);
        JClass.JavaMethod size = MEMORY_CLASS.lookupMethod("size");
        JClass.JavaMethod grow = MEMORY_CLASS.lookupMethod("grow", int.class);
        JClass.JavaMethod init = MEMORY_CLASS.lookupMethod("init", int.class, int.class, int.class, ByteBuffer.class);
        return new MemoryConvention.Delegating(null) {
            @Override
            public void emitMemLoad(IRBuilder ib, Effect effect) {
                WasmOps.WithMemArg<WasmOps.DerefType> arg = WasmOps.MEM_LOAD.cast(effect.insn().op).arg;
                ib.insert(JavaOps.insns(new InvokeDynamicInsnNode("load",
                                Type.getMethodDescriptor(BasicCallingConvention.javaType(arg.value.outType),
                                        Type.getType(Memory.class),
                                        Type.INT_TYPE),
                                Memory.Bootstrap.BOOTSTRAP_HANDLE,
                                Memory.LoadMode.fromOpcode(arg.value.getOpcode()).ordinal()))
                        .insn(memory.get(ib), getAddr(ib, arg, effect.insn().args().get(0)))
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
                                Memory.StoreMode.fromOpcode(arg.value.getOpcode()).ordinal()))
                        .insn(memory.get(ib),
                                getAddr(ib, arg, effect.insn().args().get(0)),
                                effect.insn().args().get(1))
                        .copyFrom(effect));
            }

            private Var getAddr(IRBuilder ib, WasmOps.WithMemArg<?> arg, Var addr) {
                if (arg.offset != 0) {
                    return ib.insert(JavaOps.IADD
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
                        .insn(memory.get(ib), effect.insn().args().get(0)).copyFrom(effect));
            }

            @Override
            public void emitMemInit(IRBuilder ib, Effect effect, Var data) {
                List<Var> args = new ArrayList<>();
                args.add(memory.get(ib));
                args.addAll(effect.insn().args());
                args.add(data);
                ib.insert(JavaOps.INVOKE.create(init).insn(args).copyFrom(effect));
            }

            @Override
            public void modifyConstructor(IRBuilder ib, JClass.JavaMethod ctorMethod, Module module, JClass jClass) {
                JClass.JavaMethod getAsMemory = EV_CLASS.lookupMethod("getAsMemory");
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
            public void export(ExportNode node, Module module, JClass jClass) {
                getOrMakeExports(jClass).put(node.name, memory);
            }
        };
    }

    private static void buildExports(JirPassesEvent evt) {
        JClass jClass = evt.jir;
        JClass.JavaMethod method = new JClass.JavaMethod(
                jClass,
                "getExport",
                Type.getMethodDescriptor(
                        EV_TYPE,
                        Type.getType(String.class)
                ),
                Opcodes.ACC_PUBLIC
        );
        jClass.methods.add(method);
        method.attachExt(JavaExts.METHOD_IMPL, lazy(() -> {
            Function getExport = new Function();
            getExport.attachExt(JavaExts.FUNCTION_METHOD, method);
            IRBuilder ib = new IRBuilder(getExport, getExport.newBb());
            jClass.getExt(EXPORTS_EXT).ifPresent(exports -> {
                Map<Integer, List<Map.Entry<String, ValueGetter>>> hashes = new TreeMap<>();
                for (Map.Entry<String, ValueGetter> entry : exports.entrySet()) {
                    hashes.computeIfAbsent(entry.getKey().hashCode(), $ -> new ArrayList<>())
                            .add(entry);
                }

                List<BasicBlock> targets = new ArrayList<>();

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
                        Var isKey = ib.insert(JavaOps.INVOKE.create(STRING_EQUALS).insn(
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
                Var hash = ib.insert(JavaOps.INVOKE.create(new JClass.JavaMethod(
                        STRING_CLASS,
                        "hashCode",
                        "()I",
                        Opcodes.ACC_PUBLIC
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

            return getExport;
        }));
    }

    private static class EmbedFunctionConvention extends FunctionConvention.Delegating {
        public static final UnaryOpKey<JClass.JavaMethod>.UnaryOp CREATE_HANDLE_FUNC = JavaOps.INVOKE.create(
                JClass.emptyFromJava(Func.HandleFunc.class)
                        .lookupMethod("create", ExternType.Func.class, MethodHandle.class)
        );
        public static final UnaryOpKey<JClass.JavaMethod>.UnaryOp PARSE_FUNC_TYPE = JavaOps.INVOKE.create(JClass.emptyFromJava(ExternType.Func.class)
                .lookupMethod("parse", String.class));
        private final FuncNode func;

        public EmbedFunctionConvention(FunctionConvention convention, FuncNode func) {
            super(convention);
            this.func = func;
        }

        @Override
        public void export(ExportNode node, Module module, JClass jClass) {
            delegate.export(node, module, jClass);
            getOrMakeExports(jClass).put(node.name, ib -> {
                Var exportVar = ib.func.newVar("func");
                emitFuncRef(ib,
                        WasmOps.FUNC_REF.create(node.index).insn()
                                .assignTo(exportVar)
                );
                return ib.insert(CREATE_HANDLE_FUNC
                                .insn(ib.insert(PARSE_FUNC_TYPE
                                                        .insn(ib.insert(CommonOps.constant(
                                                                        ExternType.Func.encode(Objects.requireNonNull(module
                                                                                        .getExtOrThrow(WasmExts.MODULE).types)
                                                                                .types
                                                                                .get(func.type))),
                                                                "encoded")),
                                                "ty"),
                                        exportVar),
                        "export");
            });
        }
    }

    private static class EmbedTableConvention extends TableConvention.Delegating {
        public static final UnaryOpKey<JClass.JavaMethod>.UnaryOp CREATE_HANDLE_TABLE = JavaOps.INVOKE
                .create(JClass.emptyFromJava(Table.HandleTable.class)
                        .lookupMethod("create",
                                ExternType.Table.class,
                                MethodHandle.class,
                                MethodHandle.class,
                                MethodHandle.class,
                                MethodHandle.class));
        public static final UnaryOpKey<JClass.JavaMethod>.UnaryOp CREATE_HANDLE_TABLE_TY = JavaOps.INVOKE
                .create(JClass.emptyFromJava(ExternType.Table.class)
                        .lookupMethod("create", int.class, Integer.class, byte.class));
        private final TableNode table;
        private final int idx;
        ValueGetter getExtern;
        private boolean exported = false;

        public EmbedTableConvention(TableConvention convention, TableNode table, int idx) {
            super(convention);
            this.table = table;
            this.idx = idx;
        }

        @Override
        public void modifyConstructor(IRBuilder jb, JClass.JavaMethod ctorMethod, Module module, JClass jClass) {
            super.modifyConstructor(jb, ctorMethod, module, jClass);
            if (exported) {
                ValueGetter kind = ib -> ib.insert(EmbedTableConvention.CREATE_HANDLE_TABLE_TY
                                .insn(
                                        ib.insert(CommonOps.constant(table.limits.min), "min"),
                                        IRUtils.emitNullableInt(ib, table.limits.max),
                                        ib.insert(CommonOps.constant(table.type), "ty")
                                ),
                        "kind");
                ValueGetter getHandle = effectHandle(
                        "table" + idx + "$get",
                        jClass,
                        "(I)Ljava/lang/Object;",
                        WasmOps.TABLE_REF.create(idx),
                        delegate::emitTableRef
                );
                ValueGetter setHandle = effectHandle(
                        "table" + idx + "$set",
                        jClass,
                        "(ILjava/lang/Object;)V",
                        WasmOps.TABLE_STORE.create(idx),
                        delegate::emitTableStore
                );
                ValueGetter sizeHandle = effectHandle(
                        "table" + idx + "$size",
                        jClass,
                        "()I",
                        WasmOps.TABLE_SIZE.create(idx),
                        delegate::emitTableSize
                );
                ValueGetter growHandle = effectHandle(
                        "table" + idx + "$grow",
                        jClass,
                        "(ILjava/lang/Object;)I",
                        WasmOps.TABLE_GROW.create(idx),
                        delegate::emitTableGrow
                );
                getExtern = ib -> ib.insert(CREATE_HANDLE_TABLE
                                .insn(
                                        kind.get(ib),
                                        getHandle.get(ib),
                                        setHandle.get(ib),
                                        sizeHandle.get(ib),
                                        growHandle.get(ib)
                                ),
                        "table");
            }
        }

        @Override
        public void export(ExportNode node, Module module, JClass jClass) {
            super.export(node, module, jClass);
            exported = true;
            getOrMakeExports(jClass).put(node.name, ib -> getExtern.get(ib));
        }
    }

    private static class EmbedGlobalConvention extends GlobalConvention.Delegating implements GlobalConvention {
        public static final UnaryOpKey<JClass.JavaMethod>.UnaryOp CREATE_HANDLE_GLOBAL = JavaOps.INVOKE
                .create(JClass.emptyFromJava(Global.HandleGlobal.class)
                        .lookupMethod("create", byte.class, MethodHandle.class, MethodHandle.class));
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
        public void modifyConstructor(IRBuilder ib, JClass.JavaMethod ctorMethod, Module module, JClass jClass) {
            super.modifyConstructor(ib, ctorMethod, module, jClass);
            if (exported) {
                Type jTy = BasicCallingConvention.javaType(type.type);
                getHandle = effectHandle(
                        "global" + idx + "$get",
                        jClass,
                        Type.getMethodDescriptor(jTy),
                        WasmOps.GLOBAL_REF.create(idx),
                        delegate::emitGlobalRef
                );
                setHandle = type.mut != MUT_CONST ?
                        effectHandle(
                                "global" + idx + "$set",
                                jClass,
                                Type.getMethodDescriptor(Type.VOID_TYPE, jTy),
                                WasmOps.GLOBAL_SET.create(idx),
                                delegate::emitGlobalStore
                        )
                        : jb -> jb.insert(CommonOps.constant(null), "null");
            }
        }

        @Override
        public void export(ExportNode node, Module module, JClass jClass) {
            super.export(node, module, jClass);
            exported = true;
            getOrMakeExports(jClass).put(node.name, ib -> ib.insert(CREATE_HANDLE_GLOBAL
                            .insn(
                                    ib.insert(CommonOps.constant(type.type), "ty"),
                                    getHandle.get(ib),
                                    setHandle.get(ib)
                            ),
                    "global"));
        }
    }

    private static class EmbedMemConvention extends MemoryConvention.Delegating {
        public static final UnaryOpKey<JClass.JavaMethod>.UnaryOp CREATE_HANDLE_MEMORY = JavaOps.INVOKE
                .create(JClass.emptyFromJava(Memory.HandleMemory.class)
                        .lookupMethod("create",
                                Integer.class,
                                MethodHandle.class,
                                MethodHandle.class,
                                MethodHandle.class,
                                MethodHandle.class,
                                MethodHandle.class));
        private final MemoryNode mem;
        private final int idx;
        ValueGetter getHandle, storeHandle, sizeHandle, growHandle, initHandle;
        private boolean exported;

        public EmbedMemConvention(MemoryConvention convention, MemoryNode mem, int idx) {
            super(convention);
            this.mem = mem;
            this.idx = idx;
        }

        @Override
        public void modifyConstructor(IRBuilder ctorIb, JClass.JavaMethod ctorMethod, Module module, JClass jClass) {
            super.modifyConstructor(ctorIb, ctorMethod, module, jClass);
            if (exported) {
                getHandle = effectHandle(
                        "mem" + idx + "$get",
                        jClass,
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
                                WasmOps.DerefType derefTy = WasmOps.DerefType.fromOpcode(loadMode.opcode());
                                ValueGetter handle = effectHandle(
                                        "mem" + idx + "$get$" + loadMode.toString().toLowerCase(Locale.ROOT),
                                        jClass,
                                        Type.getMethodDescriptor(
                                                BasicCallingConvention.javaType(derefTy.outType),
                                                Type.INT_TYPE
                                        ),
                                        WasmOps.MEM_LOAD.create(WasmOps.WithMemArg.create(derefTy, 0)),
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
                        jClass,
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
                                        jClass,
                                        Type.getMethodDescriptor(
                                                Type.VOID_TYPE,
                                                Type.INT_TYPE,
                                                BasicCallingConvention.javaType(storeTy.getType())
                                        ),
                                        WasmOps.MEM_STORE.create(WasmOps.WithMemArg.create(storeTy, 0)),
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
                        jClass,
                        "()I",
                        WasmOps.MEM_SIZE.create(0),
                        delegate::emitMemSize
                );
                growHandle = effectHandle(
                        "mem" + idx + "$grow",
                        jClass,
                        "(I)I",
                        WasmOps.MEM_GROW.create(0),
                        delegate::emitMemGrow
                );
                initHandle = effectHandle(
                        "mem" + idx + "$init",
                        jClass,
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
        public void export(ExportNode node, Module module, JClass jClass) {
            super.export(node, module, jClass);
            exported = true;
            getOrMakeExports(jClass).put(node.name, ib -> ib.insert(CREATE_HANDLE_MEMORY
                            .insn(
                                    IRUtils.emitNullableInt(ib, mem.limits.max),
                                    getHandle.get(ib),
                                    storeHandle.get(ib),
                                    sizeHandle.get(ib),
                                    growHandle.get(ib),
                                    initHandle.get(ib)
                            ),
                    "global"));
        }
    }
}

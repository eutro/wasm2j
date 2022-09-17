package io.github.eutro.wasm2j.conf;

import io.github.eutro.jwasm.tree.*;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ext.WasmExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.util.Instructions;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;

import java.lang.invoke.MethodHandle;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.*;

import static io.github.eutro.jwasm.Opcodes.*;
import static io.github.eutro.wasm2j.ext.CommonExts.markPure;

public class Conventions {
    public static final WirJavaConventionFactory DEFAULT_CONVENTIONS = new DefaultFactory();
    public static final DefaultCC DEFAULT_CC = new DefaultCC();

    private static class DefaultFactory implements WirJavaConventionFactory {
        protected CallingConvention getCC() {
            return DEFAULT_CC;
        }

        @Override
        public WirJavaConvention create(Module module) {
            JavaExts.JavaClass jClass = new JavaExts.JavaClass("com/example/FIXME");
            module.attachExt(JavaExts.JAVA_CLASS, jClass);

            ModuleNode node = module.getExtOrThrow(WasmExts.MODULE);
            Map<ExprNode, Function> funcMap = module.getExtOrThrow(WasmExts.FUNC_MAP);

            List<JavaExts.JavaMethod> funcs = new ArrayList<>();
            List<JavaExts.JavaField> lGlobals = new ArrayList<>();
            List<JavaExts.JavaField> lMems = new ArrayList<>();
            List<JavaExts.JavaField> lTables = new ArrayList<>();

            if (node.imports != null && node.imports.imports != null) {
                for (AbstractImportNode importNode : node.imports) {
                    if (importNode.importType() == IMPORTS_FUNC) {
                        FuncImportNode funcImport = (FuncImportNode) importNode;
                        assert node.types != null && node.types.types != null;
                        JavaExts.JavaMethod method = new JavaExts.JavaMethod(
                                jClass,
                                importNode.name,
                                getCC().getDescriptor(node.types.types.get(funcImport.type))
                                        .getDescriptor(),
                                JavaExts.JavaMethod.Type.ABSTRACT
                        );
                        funcs.add(method);
                        jClass.methods.add(method);
                    } else {
                        throw new UnsupportedOperationException();
                    }
                }
            }

            if (node.funcs != null && node.funcs.funcs != null) {
                assert node.types != null && node.types.types != null;
                assert node.codes != null && node.codes.codes != null;
                int i = 0;
                Iterator<CodeNode> it = node.codes.codes.iterator();
                for (FuncNode fn : node.funcs) {
                    TypeNode typeNode = node.types.types.get(fn.type);
                    JavaExts.JavaMethod method = new JavaExts.JavaMethod(
                            jClass,
                            "func" + i++,
                            getCC().getDescriptor(typeNode).getDescriptor(),
                            JavaExts.JavaMethod.Type.FINAL
                    );
                    jClass.methods.add(method);
                    funcs.add(method);
                    Function implFunc = funcMap.get(it.next().expr);
                    method.attachExt(JavaExts.METHOD_IMPL, implFunc);
                    implFunc.attachExt(JavaExts.FUNCTION_DESCRIPTOR, method.descriptor);
                    implFunc.attachExt(JavaExts.FUNCTION_OWNER, jClass);
                }
            }

            if (node.globals != null) {
                int i = 0;
                for (GlobalNode global : node.globals) {
                    JavaExts.JavaField field = new JavaExts.JavaField(
                            jClass,
                            "global" + i++,
                            DefaultCC.javaType(global.type.type).getDescriptor(),
                            false
                    );
                    jClass.fields.add(field);
                    lGlobals.add(field);
                }
            }

            if (node.mems != null) {
                int i = 0;
                for (MemoryNode ignored : node.mems) {
                    JavaExts.JavaField field = new JavaExts.JavaField(
                            jClass,
                            "mem" + i++,
                            Type.getDescriptor(ByteBuffer.class),
                            false
                    );
                    jClass.fields.add(field);
                    lMems.add(field);
                }
            }

            if (node.tables != null) {
                int i = 0;
                for (TableNode table : node.tables) {
                    JavaExts.JavaField field = new JavaExts.JavaField(
                            jClass,
                            "table" + i++,
                            "[" + DefaultCC.javaType(table.type).getDescriptor(),
                            false
                    );
                    jClass.fields.add(field);
                    lTables.add(field);
                }
            }

            if (node.exports != null) {
                for (ExportNode export : node.exports) {
                    switch (export.type) {
                        case EXPORTS_FUNC:
                            JavaExts.JavaMethod func = funcs.get(export.index);
                            func.type = JavaExts.JavaMethod.Type.VIRTUAL;
                            func.name = export.name;
                            break;
                        case EXPORTS_TABLE:
                            JavaExts.JavaField table = lTables.get(export.index);
                            table.name = export.name;
                            table.otherAccess = (table.otherAccess & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
                            break;
                        case EXPORTS_MEM:
                            JavaExts.JavaField mem = lMems.get(export.index);
                            mem.name = export.name;
                            mem.otherAccess = (mem.otherAccess & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
                            break;
                        case EXPORTS_GLOBAL:
                            JavaExts.JavaField glob = lGlobals.get(export.index);
                            glob.name = export.name;
                            glob.otherAccess = (glob.otherAccess & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
                            break;
                        default:
                            throw new AssertionError();
                    }
                }
            }

            return new WirJavaConvention() {
                private Var getThis(IRBuilder ib) {
                    return ib.insert(JavaOps.THIS.insn(), "this");
                }

                @Override
                public void buildConstructor() {
                    JavaExts.JavaMethod ctorMethod = new JavaExts.JavaMethod(
                            jClass,
                            "<init>",
                            "()V",
                            JavaExts.JavaMethod.Type.VIRTUAL
                    );
                    jClass.methods.add(ctorMethod);
                    {
                        Function ctorImpl = new Function();
                        module.funcions.add(ctorImpl);
                        ctorImpl.attachExt(JavaExts.FUNCTION_DESCRIPTOR, ctorMethod.descriptor);
                        ctorImpl.attachExt(JavaExts.FUNCTION_OWNER, ctorMethod.owner);
                        ctorMethod.attachExt(JavaExts.METHOD_IMPL, ctorImpl);

                        IRBuilder ib = new IRBuilder(ctorImpl, ctorImpl.newBb());
                        Var thisVar = ib.insert(JavaOps.THIS.insn(), "this");
                        ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                new JavaExts.JavaClass("java/lang/Object"),
                                "<init>",
                                "()V",
                                JavaExts.JavaMethod.Type.FINAL
                        )).insn(thisVar).assignTo());

                        Var outVar = ib.insert(JavaOps.GET_FIELD.create(new JavaExts.JavaField(
                                new JavaExts.JavaClass("java/lang/System"),
                                "out",
                                "Ljava/io/PrintStream;",
                                true
                        )).insn(), "out");
                        Var hwString = ib.insert(CommonOps.CONST.create("Hello, world!").insn(), "hwString");
                        ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                new JavaExts.JavaClass("java/io/PrintStream"),
                                "println",
                                "(Ljava/lang/String;)V",
                                JavaExts.JavaMethod.Type.VIRTUAL
                        )).insn(outVar, hwString).assignTo());

                        ib.insertCtrl(CommonOps.RETURN.insn().jumpsTo());
                    }
                }

                @Override
                public void emitGlobalRef(IRBuilder ib, Effect effect) {
                    int global = WasmOps.GLOBAL_REF.cast(effect.insn().op).arg;
                    ib.insert(JavaOps.GET_FIELD.create(lGlobals.get(global))
                            .insn(getThis(ib))
                            .copyFrom(effect));
                }

                @Override
                public void emitGlobalStore(IRBuilder ib, Effect effect) {
                    int global = WasmOps.GLOBAL_SET.cast(effect.insn().op).arg;
                    ib.insert(JavaOps.PUT_FIELD.create(lGlobals.get(global))
                            .insn(getThis(ib), effect.insn().args.get(0))
                            .copyFrom(effect));
                }

                @Override
                public void emitCall(IRBuilder ib, Effect effect) {
                    WasmOps.CallType callType = WasmOps.CALL.cast(effect.insn().op).arg;
                    JavaExts.JavaMethod method = funcs.get(callType.func);
                    List<Var> args = new ArrayList<>();
                    args.add(getThis(ib));
                    List<Var> rawArgs = getCC().passArguments(ib, effect.insn().args, callType.type);
                    args.addAll(rawArgs);
                    CallingConvention cc = getCC();
                    Var[] rets = cc.getDescriptor(callType.type).getReturnType() == Type.VOID_TYPE
                            ? new Var[0]
                            : new Var[]{ib.func.newVar("ret")};
                    ib.insert(JavaOps.INVOKE.create(method)
                            .insn(args)
                            .assignTo(rets));
                    ib.insert(CommonOps.IDENTITY
                            .insn(cc.receiveReturn(ib, rawArgs, rets.length == 1 ? rets[0] : null, callType.type))
                            .copyFrom(effect));
                }

                @Override
                public void emitCallIndirect(IRBuilder ib, Effect effect) {
                    TypeNode callType = WasmOps.CALL_INDIRECT.cast(effect.insn().op).arg;
                    CallingConvention cc = getCC();
                    List<Var> rawArgs = cc.passArguments(ib, effect.insn().args, callType);
                    Type ty = cc.getDescriptor(callType);
                    Var[] rets = ty.getReturnType() == Type.VOID_TYPE
                            ? new Var[0]
                            : new Var[]{ib.func.newVar("ret")};
                    ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                    new JavaExts.JavaClass(Type.getInternalName(MethodHandle.class)),
                                    "invokeExact",
                                    ty.getDescriptor(),
                                    JavaExts.JavaMethod.Type.VIRTUAL
                            ))
                            .insn(rawArgs)
                            .assignTo(rets));
                    ib.insert(CommonOps.IDENTITY
                            .insn(cc.receiveReturn(ib, rawArgs, rets.length == 1 ? rets[0] : null, callType))
                            .copyFrom(effect));
                }

                @Override
                public void emitMemLoad(IRBuilder ib, Effect effect) {
                    WasmOps.WithMemArg<WasmOps.DerefType> wmArg = WasmOps.MEM_LOAD.cast(effect.insn().op).arg;
                    WasmOps.DerefType derefType = wmArg.value;

                    JavaExts.JavaMethod toInvoke = new JavaExts.JavaMethod(
                            new JavaExts.JavaClass(Type.getInternalName(ByteBuffer.class)),
                            derefType.load.funcName,
                            derefType.load.desc,
                            JavaExts.JavaMethod.Type.VIRTUAL
                    );
                    Var ptr = effect.insn().args.get(0);
                    Insn loadInsn = JavaOps.INVOKE.create(toInvoke).insn(getMem(ib), getAddr(ib, wmArg, ptr));
                    if (derefType.ext.insns.size() == 0) {
                        ib.insert(loadInsn.copyFrom(effect));
                    } else {
                        Var loaded = ib.insert(loadInsn, "loaded");
                        ib.insert(JavaOps.INSNS
                                .create(Instructions.copyList(derefType.ext.insns))
                                .insn(loaded)
                                .copyFrom(effect));
                    }
                }

                @Override
                public void emitMemStore(IRBuilder ib, Effect effect) {
                    WasmOps.WithMemArg<WasmOps.StoreType> wmArg = WasmOps.MEM_STORE.cast(effect.insn().op).arg;
                    InsnList insns = Instructions.copyList(wmArg.value.insns);
                    insns.add(new InsnNode(Opcodes.POP));
                    ib.insert(JavaOps.INSNS
                            .create(insns)
                            .insn(
                                    getMem(ib),
                                    getAddr(ib, wmArg, effect.insn().args.get(1)),
                                    effect.insn().args.get(0)
                            )
                            .assignTo());
                }

                private Var getAddr(IRBuilder ib, WasmOps.WithMemArg<?> wmArg, Var ptr) {
                    return ib.insert(markPure(JavaOps.insns(new InsnNode(Opcodes.IADD))).insn(
                                    ptr,
                                    ib.insert(CommonOps.CONST.create(wmArg.offset).insn(),
                                            "offset")),
                            "addr");
                }

                private Var getMem(IRBuilder ib) {
                    return ib.insert(JavaOps.GET_FIELD
                                    .create(lMems.get(0))
                                    .insn(getThis(ib)),
                            "mem");
                }

                @Override
                public void emitTableRef(IRBuilder ib, Effect effect) {
                    int table = WasmOps.TABLE_REF.cast(effect.insn().op).arg;
                    Var tableV = ib.insert(JavaOps.GET_FIELD
                                    .create(lTables.get(table))
                                    .insn(getThis(ib)),
                            "table");
                    ib.insert(JavaOps.ARRAY_GET.create()
                            .insn(tableV, effect.insn().args.get(0))
                            .copyFrom(effect));
                }

                @Override
                public void emitTableStore(IRBuilder ib, Effect effect) {
                    int table = WasmOps.TABLE_STORE.cast(effect.insn().op).arg;
                    Var tableV = ib.insert(JavaOps.GET_FIELD
                                    .create(lTables.get(table))
                                    .insn(getThis(ib)),
                            "table");
                    ib.insert(JavaOps.ARRAY_SET.create()
                            .insn(tableV,
                                    effect.insn().args.get(0),
                                    effect.insn().args.get(1))
                            .assignTo());
                }

                @Override
                public void emitFuncRef(IRBuilder ib, Effect effect) {
                    int func = WasmOps.FUNC_REF.cast(effect.insn().op).arg;
                    Var handle = ib.insert(JavaOps.HANDLE_OF
                                    .create(funcs.get(func)).insn(),
                            "handle");
                    ib.insert(JavaOps.INVOKE
                            .create(new JavaExts.JavaMethod(
                                    new JavaExts.JavaClass(Type.getInternalName(MethodHandle.class)),
                                    "bindTo",
                                    "(Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;",
                                    JavaExts.JavaMethod.Type.VIRTUAL
                            ))
                            .insn(handle, getThis(ib))
                            .copyFrom(effect));
                }

                @Override
                public void emitMemSize(IRBuilder ib, Effect effect) {
                    ib.insert(JavaOps.INVOKE
                            .create(new JavaExts.JavaMethod(
                                    new JavaExts.JavaClass(Type.getInternalName(Buffer.class)),
                                    "capacity",
                                    "()I",
                                    JavaExts.JavaMethod.Type.VIRTUAL
                            ))
                            .copyFrom(effect));
                }

                @Override
                public void emitMemGrow(IRBuilder ib, Effect effect) {
                    ib.insert(CommonOps.CONST.create(0).insn().copyFrom(effect));
                }
            };
        }
    }

    private static class DefaultCC implements CallingConvention {
        public static Type javaType(byte type) {
            switch (type) {
                case I32:
                    return Type.INT_TYPE;
                case I64:
                    return Type.LONG_TYPE;
                case F32:
                    return Type.FLOAT_TYPE;
                case F64:
                    return Type.DOUBLE_TYPE;
                case FUNCREF:
                    return Type.getType(MethodHandle.class);
                case EXTERNREF:
                    return Type.getType(Object.class);
                default:
                    throw new IllegalArgumentException("Not a type");
            }
        }

        private static Type methodDesc(TypeNode tn) {
            return methodDesc(tn.params, tn.returns);
        }

        private static Type methodDesc(byte[] params, byte[] returns) {
            Type[] args = new Type[params.length];
            for (int i = 0; i < params.length; i++) {
                args[i] = javaType(params[i]);
            }
            return Type.getMethodType(returnType(returns), args);
        }

        private static Type returnType(byte[] returns) {
            switch (returns.length) {
                case 0:
                    return Type.VOID_TYPE;
                case 1:
                    return javaType(returns[0]);
                default:
                    Type lastType = javaType(returns[0]);
                    for (int i = 1; i < returns.length; i++) {
                        if (!lastType.equals(javaType(returns[i]))) {
                            lastType = Type.getType(Object.class);
                            break;
                        }
                    }
                    return Type.getType("[" + lastType);
            }
        }

        @Override
        public Type getDescriptor(TypeNode funcType) {
            return methodDesc(funcType);
        }

        @Override
        public List<Var> passArguments(IRBuilder ib, List<Var> args, TypeNode funcType) {
            return args;
        }

        @Override
        public List<Var> receiveArguments(IRBuilder ib, List<Var> rawArgs, TypeNode funcType) {
            return rawArgs;
        }

        @Override
        public Optional<Var> emitReturn(IRBuilder ib, List<Var> rawArgs, List<Var> returns, TypeNode funcType) {
            switch (returns.size()) {
                case 0:
                    return Optional.empty();
                case 1:
                    return Optional.of(returns.get(0));
                default:
                    throw new UnsupportedOperationException("multiple returns");
            }
        }

        @Override
        public List<Var> receiveReturn(IRBuilder ib, List<Var> rawArgs, Var rawReturn, TypeNode funcType) {
            return rawReturn == null ? Collections.emptyList() : Collections.singletonList(rawReturn);
        }
    }
}

package io.github.eutro.wasm2j.conf;

import io.github.eutro.jwasm.tree.*;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ext.WasmExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.ssa.Module;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;

import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static io.github.eutro.jwasm.Opcodes.*;

public class Conventions {
    public static final WirJavaConventionFactory DEFAULT_CONVENTIONS = new DefaultFactory();

    private static class DefaultFactory implements WirJavaConventionFactory {
        @Override
        public WirJavaConvention create(Module module) {
            JavaExts.JavaClass jClass = new JavaExts.JavaClass("com/example/FIXME");
            module.attachExt(JavaExts.JAVA_CLASS, jClass);

            ModuleNode node = module.getExtOrThrow(WasmExts.MODULE);
            Map<ExprNode, Function> funcMap = module.getExtOrThrow(WasmExts.FUNC_MAP);

            List<JavaExts.JavaMethod> funcs = new ArrayList<>();
            List<JavaExts.JavaField> globals = new ArrayList<>();
            List<JavaExts.JavaField> memories = new ArrayList<>();
            List<JavaExts.JavaField> tables = new ArrayList<>();

            if (node.imports != null && node.imports.imports != null) {
                throw new UnsupportedOperationException(); // FIXME
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
                            methodDesc(typeNode).getDescriptor(), // FIXME
                            JavaExts.JavaMethod.Type.FINAL
                    );
                    jClass.methods.add(method);
                    funcs.add(method);
                    Function implFunc = funcMap.get(it.next().expr);
                    method.attachExt(JavaExts.METHOD_IMPL, implFunc);
                    implFunc.attachExt(JavaExts.FUNCTION_DESCRIPTOR, method.descriptor);
                }
            }

            if (node.globals != null) {
                int i = 0;
                for (GlobalNode global : node.globals) {
                    JavaExts.JavaField field = new JavaExts.JavaField(
                            jClass,
                            "global" + i++,
                            javaType(global.type.type).getDescriptor(), // FIXME
                            false
                    );
                    jClass.fields.add(field);
                    globals.add(field);
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
                    memories.add(field);
                }
            }

            if (node.tables != null) {
                int i = 0;
                for (TableNode table : node.tables) {
                    JavaExts.JavaField field = new JavaExts.JavaField(
                            jClass,
                            "table" + i++,
                            "[" + javaType(table.type).getDescriptor(),
                            false
                    );
                    jClass.fields.add(field);
                    tables.add(field);
                }
            }

            return new WirJavaConvention() {
                private Var getThis(IRBuilder ib) {
                    return ib.insert(JavaOps.THIS.insn(), "this");
                }

                @Override
                public void emitGlobalRef(IRBuilder ib, Effect effect) {
                    int global = WasmOps.GLOBAL_REF.cast(effect.insn().op).arg;
                    ib.insert(JavaOps.GET_FIELD.create(globals.get(global))
                            .insn(getThis(ib))
                            .copyFrom(effect));
                }

                @Override
                public void emitGlobalStore(IRBuilder ib, Effect effect) {
                    int global = WasmOps.GLOBAL_SET.cast(effect.insn().op).arg;
                    ib.insert(JavaOps.PUT_FIELD.create(globals.get(global))
                            .insn(getThis(ib), effect.insn().args.get(0))
                            .copyFrom(effect));
                }

                @Override
                public void emitCall(IRBuilder ib, Effect effect) {
                    WasmOps.CallType callType = WasmOps.CALL.cast(effect.insn().op).arg;
                    JavaExts.JavaMethod method = funcs.get(callType.func);
                    List<Var> args = new ArrayList<>();
                    args.add(getThis(ib));
                    args.addAll(effect.insn().args);
                    ib.insert(JavaOps.INVOKE.create(method)
                            .insn(args)
                            .copyFrom(effect));
                }

                @Override
                public void emitCallIndirect(IRBuilder ib, Effect effect) {
                    TypeNode callType = WasmOps.CALL_INDIRECT.cast(effect.insn().op).arg;
                    ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                    new JavaExts.JavaClass(Type.getInternalName(MethodHandle.class)),
                                    "invokeExact",
                                    methodDesc(callType).getDescriptor(),
                                    JavaExts.JavaMethod.Type.VIRTUAL
                            ))
                            .copyFrom(effect));
                }

                @Override
                public void emitMemLoad(IRBuilder ib, Effect effect) {
                    WasmOps.DerefType derefType = WasmOps.MEM_LOAD.cast(effect.insn().op).arg;
                    Var mem = ib.insert(JavaOps.GET_FIELD
                                    .create(memories.get(0))
                                    .insn(getThis(ib)),
                            "mem");
                    List<Var> args = new ArrayList<>();
                    args.add(mem);
                    args.addAll(effect.insn().args);

                    JavaExts.JavaMethod toInvoke = new JavaExts.JavaMethod(
                            new JavaExts.JavaClass(Type.getInternalName(ByteBuffer.class)),
                            null,
                            null,
                            JavaExts.JavaMethod.Type.VIRTUAL
                    );
                    Var loaded = ib.insert(JavaOps.INVOKE.create(toInvoke).insn(args), "loaded");

                    Type type = javaType(derefType.outType);
                    if (type == Type.FLOAT_TYPE) {
                        toInvoke.name = "getFloat";
                        toInvoke.descriptor = "(I)F";
                    } else if (type == Type.DOUBLE_TYPE) {
                        toInvoke.name = "getDouble";
                        toInvoke.descriptor = "(I)D";
                    } else {
                        switch (derefType.loadBytes) {
                            case 1:
                                toInvoke.name = "getByte";
                                toInvoke.descriptor = "(I)B";
                                break;
                            case 2:
                                toInvoke.name = "getShort";
                                toInvoke.descriptor = "(I)S";
                                break;
                            case 4:
                                toInvoke.name = "getInt";
                                toInvoke.descriptor = "(I)I";
                                break;
                            case 8:
                                toInvoke.name = "getLong";
                                toInvoke.descriptor = "(I)J";
                                break;
                            default:
                                throw new AssertionError();
                        }
                        if (type == Type.LONG_TYPE) {
                            InsnList il = new InsnList();
                            il.add(new InsnNode(Opcodes.I2L));
                            loaded = ib.insert(JavaOps.INSNS.create(il).insn(loaded), "cast");
                        }
                    }
                    ib.insert(CommonOps.IDENTITY.insn(loaded).copyFrom(effect));
                }

                @Override
                public void emitMemStore(IRBuilder ib, Effect effect) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void emitTableRef(IRBuilder ib, Effect effect) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void emitTableStore(IRBuilder ib, Effect effect) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void emitFuncRef(IRBuilder ib, Effect effect) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void emitMemSize(IRBuilder ib, Effect effect) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void emitMemGrow(IRBuilder ib, Effect effect) {
                    throw new UnsupportedOperationException();
                }
            };
        }

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

        public static Type methodDesc(TypeNode tn) {
            return methodDesc(tn.params, tn.returns);
        }

        public static Type methodDesc(byte[] params, byte[] returns) {
            Type[] args = new Type[params.length];
            for (int i = 0; i < params.length; i++) {
                args[i] = javaType(params[i]);
            }
            return Type.getMethodType(returnType(returns), args);
        }

        public static Type returnType(byte[] returns) {
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
    }
}

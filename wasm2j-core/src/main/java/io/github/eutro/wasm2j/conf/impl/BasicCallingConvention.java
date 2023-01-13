package io.github.eutro.wasm2j.conf.impl;

import io.github.eutro.jwasm.tree.TypeNode;
import io.github.eutro.wasm2j.conf.api.CallingConvention;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Insn;
import io.github.eutro.wasm2j.ssa.Var;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.github.eutro.jwasm.Opcodes.*;

public class BasicCallingConvention implements CallingConvention {
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
            case V128:
                return Type.getType(ByteBuffer.class);
            default:
                throw new IllegalArgumentException("Not a type");
        }
    }

    public static Type boxedType(byte type) {
        switch (type) {
            case I32:
                return Type.getType(Integer.class);
            case I64:
                return Type.getType(Long.class);
            case F32:
                return Type.getType(Float.class);
            case F64:
                return Type.getType(Double.class);
            case FUNCREF:
                return Type.getType(MethodHandle.class);
            case EXTERNREF:
                return Type.getType(Object.class);
            case V128:
                return Type.getType(ByteBuffer.class);
            default:
                throw new IllegalArgumentException("Not a type");
        }
    }

    public static Var maybeBoxed(IRBuilder ib, Var val, byte fromTy, Type toTy) {
        Type unboxedTy = javaType(fromTy);
        if (toTy == unboxedTy) return val;
        switch (fromTy) {
            case I32:
            case I64:
            case F32:
            case F64: {
                Type boxedTy = boxedType(fromTy);
                return ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                        new JavaExts.JavaClass(boxedTy.getInternalName()),
                                        "valueOf",
                                        Type.getMethodType(boxedTy, unboxedTy).getDescriptor(),
                                        JavaExts.JavaMethod.Kind.STATIC))
                                .insn(val),
                        "boxed");
            }
            case FUNCREF:
            case EXTERNREF:
                return val;
            default:
                throw new IllegalArgumentException("Not a type");
        }
    }

    public static Var unboxed(IRBuilder ib, Var val, Type fromTy, byte toTy) {
        Type unboxedTy = javaType(toTy);
        if (fromTy == unboxedTy) return val;
        String methodName = null;
        switch (toTy) {
            case I32:
                methodName = "intValue";
            case I64:
                if (methodName == null) methodName = "longValue";
            case F32:
                if (methodName == null) methodName = "floatValue";
            case F64:
                if (methodName == null) methodName = "doubleValue";
            {
                return ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                new JavaExts.JavaClass(Type.getInternalName(Number.class)),
                                methodName,
                                Type.getMethodType(unboxedTy).getDescriptor(),
                                JavaExts.JavaMethod.Kind.VIRTUAL
                        ))
                        .insn(ib.insert(JavaOps.insns(new TypeInsnNode(Opcodes.CHECKCAST, Type.getInternalName(Number.class)))
                                        .insn(val),
                                "cast")
                        ), "unboxed");
            }
            case FUNCREF:
                return ib.insert(JavaOps.insns(new TypeInsnNode(Opcodes.CHECKCAST, unboxedTy.getInternalName()))
                                .insn(val),
                        "cast");
            case EXTERNREF:
                return val;
            default:
                throw new IllegalArgumentException("Not a type");
        }
    }

    private static Type methodDesc(TypeNode tn) {
        return methodDesc(tn.params, tn.returns);
    }

    public static Type methodDesc(byte[] params, byte[] returns) {
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
            default: {
                Type elemTy = returnType(funcType.returns).getElementType();
                MethodNode mn = new MethodNode();
                new InstructionAdapter(mn).newarray(elemTy);
                Var returnArr = ib.insert(JavaOps.insns(mn.instructions)
                                .insn(ib.insert(
                                        CommonOps.constant(funcType.returns.length),
                                        "len"
                                )),
                        "returns");
                int i = 0;
                for (Var returnVal : returns) {
                    byte retTy = funcType.returns[i];
                    ib.insert(JavaOps.ARRAY_SET
                            .create()
                            .insn(
                                    returnArr,
                                    ib.insert(CommonOps.constant(i), "i"),
                                    maybeBoxed(ib, returnVal, retTy, elemTy)
                            )
                            .assignTo());
                    i++;
                }
                return Optional.of(returnArr);
            }
        }
    }

    @Override
    public List<Var> receiveReturn(IRBuilder ib, List<Var> rawArgs, Var rawReturn, TypeNode funcType) {
        switch (funcType.returns.length) {
            case 0:
                return Collections.emptyList();
            case 1:
                return Collections.singletonList(rawReturn);
            default: {
                Type elemTy = returnType(funcType.returns).getElementType();
                int i = 0;
                List<Var> returns = new ArrayList<>();
                for (byte retTy : funcType.returns) {
                    Insn insn = CommonOps.constant(i++);
                    returns.add(unboxed(
                            ib,
                            ib.insert(JavaOps.ARRAY_GET.create()
                                            .insn(
                                                    rawReturn,
                                                    ib.insert(insn, "i")
                                            ),
                                    "retRaw"),
                            elemTy,
                            retTy));
                }
                return returns;
            }
        }
    }
}

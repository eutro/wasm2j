package io.github.eutro.wasm2j.core.conf.impl;

import io.github.eutro.jwasm.tree.TypeNode;
import io.github.eutro.wasm2j.core.conf.itf.CallingConvention;
import io.github.eutro.wasm2j.core.ops.CommonOps;
import io.github.eutro.wasm2j.core.ssa.IRBuilder;
import io.github.eutro.wasm2j.core.ssa.Var;
import io.github.eutro.wasm2j.core.ops.JavaOps;
import io.github.eutro.wasm2j.core.ssa.Insn;
import io.github.eutro.wasm2j.core.ssa.JClass;
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

/**
 * The ubiquitous calling convention.
 * <p>
 * Parameters are passed directly, and return values,
 * if there are more than one, are returned in an array.
 * <p>
 * If all return types are the same, the component
 * of the array type is that type. Otherwise, the component
 * of the array type is {@link Object}, and all values are boxed.
 */
public class BasicCallingConvention implements CallingConvention {
    /**
     * A singleton instance of this calling convention.
     */
    public static final BasicCallingConvention INSTANCE = new BasicCallingConvention();

    private static final JClass NUMBER_CLASS = JClass.emptyFromJava(Number.class);

    /**
     * Convert from a WebAssembly type to a Java type.
     *
     * @param type The WebAssembly type.
     * @return The Java type.
     */
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
                throw new IllegalArgumentException(String.format("Not a type: 0x%02x", type));
        }
    }

    /**
     * Convert from a WebAssembly type to a non-primitive Java type.
     *
     * @param type The WebAssembly type.
     * @return The Java type.
     */
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

    /**
     * Convert a WebAssembly value into a Java reference.
     *
     * @param ib     The instruction builder.
     * @param val    The WebAssembly value.
     * @param fromTy The source WebAssembly type.
     * @param toTy   The target Java type.
     * @return The boxed Java value.
     */
    public static Var maybeBoxed(IRBuilder ib, Var val, byte fromTy, Type toTy) {
        Type unboxedTy = javaType(fromTy);
        if (toTy == unboxedTy) return val;
        switch (fromTy) {
            case I32:
            case I64:
            case F32:
            case F64: {
                Type boxedTy = boxedType(fromTy);
                return ib.insert(JavaOps.INVOKE.create(new JClass.JavaMethod(
                                        new JClass(boxedTy.getInternalName()),
                                        "valueOf",
                                        Type.getMethodType(boxedTy, unboxedTy).getDescriptor(),
                                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
                                ))
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

    /**
     * Convert a Java reference into a WebAssembly value.
     *
     * @param ib     The instruction builder.
     * @param val    The WebAssembly value.
     * @param fromTy The source Java type.
     * @param toTy   The target WebAssembly type.
     * @return The unboxed WebAssembly value.
     */
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
                return ib.insert(JavaOps.INVOKE.create(new JClass.JavaMethod(
                                NUMBER_CLASS,
                                methodName,
                                Type.getMethodType(unboxedTy).getDescriptor(),
                                Opcodes.ACC_PUBLIC
                        ))
                        .insn(ib.insert(JavaOps.insns(new TypeInsnNode(Opcodes.CHECKCAST, NUMBER_CLASS.name))
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

    /**
     * Return the {@link BasicCallingConvention} descriptor of the given WebAssembly function type.
     *
     * @param params  The WebAssembly parameter types.
     * @param returns The WebAssembly return types.
     * @return The descriptor of the Java method.
     */
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

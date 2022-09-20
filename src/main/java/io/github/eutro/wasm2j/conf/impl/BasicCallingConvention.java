package io.github.eutro.wasm2j.conf.impl;

import io.github.eutro.jwasm.tree.TypeNode;
import io.github.eutro.wasm2j.conf.api.CallingConvention;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Var;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
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

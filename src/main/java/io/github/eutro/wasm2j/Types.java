package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.tree.TypeNode;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnNode;

import java.lang.invoke.MethodHandle;

import static io.github.eutro.jwasm.Opcodes.*;
import static org.objectweb.asm.Opcodes.*;

class Types {
    public static Type toJava(byte type) {
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
            args[i] = toJava(params[i]);
        }
        return Type.getMethodType(returnType(returns), args);
    }

    public static Type returnType(byte[] returns) {
        switch (returns.length) {
            case 0:
                return Type.VOID_TYPE;
            case 1:
                return Types.toJava(returns[0]);
            default:
                Type lastType = Types.toJava(returns[0]);
                for (int i = 1; i < returns.length; i++) {
                    if (!lastType.equals(Types.toJava(returns[i]))) {
                        lastType = Type.getType(Object.class);
                        break;
                    }
                }
                return Type.getType("[" + lastType);
        }
    }
}

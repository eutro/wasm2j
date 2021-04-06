package io.github.eutro.wasm2j;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.InstructionAdapter;
import org.objectweb.asm.tree.*;

import java.util.function.Consumer;

import static io.github.eutro.jwasm.Opcodes.*;
import static org.objectweb.asm.Opcodes.*;

class Util {
    public static InsnList makeList(AbstractInsnNode... nodes) {
        InsnList list = new InsnList();
        for (AbstractInsnNode node : nodes) {
            list.add(node);
        }
        return list;
    }

    @NotNull
    public static MethodInsnNode staticNode(String owner, String name, String desc) {
        return new MethodInsnNode(INVOKESTATIC, owner, name, desc, false);
    }

    @NotNull
    public static MethodInsnNode virtualNode(String owner, String name, String desc) {
        return new MethodInsnNode(INVOKEVIRTUAL, owner, name, desc, false);
    }

    @NotNull
    public static AbstractInsnNode constant(Object v) {
        if (v instanceof Integer || v instanceof Long || v instanceof Float || v instanceof Double) {
            MethodNode mn = new MethodNode();
            InstructionAdapter ia = new InstructionAdapter(mn);
            if (v instanceof Integer) {
                ia.iconst((Integer) v);
            } else if (v instanceof Long) {
                ia.lconst((Long) v);
            } else if (v instanceof Float) {
                ia.fconst((Float) v);
            } else {
                ia.dconst((Double) v);
            }
            return mn.instructions.getFirst();
        }
        return v == null ? new InsnNode(ACONST_NULL) : new LdcInsnNode(v);
    }

    public static AbstractInsnNode defaultValue(Type type) {
        switch (type.getSort()) {
            case Type.BOOLEAN:
            case Type.BYTE:
            case Type.SHORT:
            case Type.CHAR:
            case Type.INT:
                return new InsnNode(ICONST_0);
            case Type.FLOAT:
                return new InsnNode(FCONST_0);
            case Type.LONG:
                return new InsnNode(LCONST_0);
            case Type.DOUBLE:
                return new InsnNode(DCONST_0);
            case Type.OBJECT:
            case Type.ARRAY:
                return new InsnNode(ACONST_NULL);
            default:
                throw new IllegalArgumentException();
        }
    }
}

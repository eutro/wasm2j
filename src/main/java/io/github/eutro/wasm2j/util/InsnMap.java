package io.github.eutro.wasm2j.util;

import io.github.eutro.jwasm.Opcodes;
import io.github.eutro.jwasm.tree.AbstractInsnNode;
import io.github.eutro.jwasm.tree.PrefixInsnNode;
import io.github.eutro.jwasm.tree.VectorInsnNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class InsnMap<T> {
    @SuppressWarnings("unchecked")
    public final T[] singleOpcode = (T[]) new Object[(int) Byte.MAX_VALUE - (int) Byte.MIN_VALUE];
    public final HashMap<Integer, T> prefixOpcode = new HashMap<>();
    public final HashMap<Integer, T> vectorOpcode = new HashMap<>();

    public T get(AbstractInsnNode insn) {
        if (insn instanceof PrefixInsnNode) {
            return getInt(((PrefixInsnNode) insn).intOpcode);
        } else if (insn instanceof VectorInsnNode) {
            return getVector(((VectorInsnNode) insn).intOpcode);
        } else {
            return getByte(insn.opcode);
        }
    }

    public T get(byte opcode, int intOpcode) {
        switch (opcode) {
            case Opcodes.INSN_PREFIX:
                return getInt(intOpcode);
            case Opcodes.VECTOR_PREFIX:
                return getVector(intOpcode);
            default:
                return getByte(opcode);
        }
    }

    public Collection<T> getValues() {
        List<T> values = new ArrayList<>();
        for (T t : singleOpcode) {
            if (t != null) {
                values.add(t);
            }
        }
        values.addAll(prefixOpcode.values());
        values.addAll(vectorOpcode.values());
        return values;
    }

    public T getByte(byte opcode) {
        return singleOpcode[Byte.toUnsignedInt(opcode)];
    }

    public T getInt(int opcode) {
        return prefixOpcode.get(opcode);
    }

    private T getVector(int intOpcode) {
        return vectorOpcode.get(intOpcode);
    }

    public void put(byte opcode, int intOpcode, T value) {
        switch (opcode) {
            case Opcodes.INSN_PREFIX:
                putInt(intOpcode, value);
                break;
            case Opcodes.VECTOR_PREFIX:
                putVector(intOpcode, value);
                break;
            default:
                putByte(opcode, value);
                break;
        }
    }

    public void putByte(byte opcode, T value) {
        singleOpcode[Byte.toUnsignedInt(opcode)] = value;
    }

    public void putByte(byte[] opcodes, T value) {
        for (byte opcode : opcodes) {
            putByte(opcode, value);
        }
    }

    public void putInt(int opcode, T value) {
        prefixOpcode.put(opcode, value);
    }

    public void putInt(int[] opcodes, T value) {
        for (int opcode : opcodes) {
            putInt(opcode, value);
        }
    }

    public void putVector(int opcode, T value) {
        vectorOpcode.put(opcode, value);
    }

    public void putVector(int[] opcodes, T value) {
        for (int opcode : opcodes) {
            putVector(opcode, value);
        }
    }
}

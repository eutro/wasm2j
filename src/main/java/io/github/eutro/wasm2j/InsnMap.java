package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.tree.AbstractInsnNode;
import io.github.eutro.jwasm.tree.PrefixInsnNode;

import java.util.HashMap;

public class InsnMap<T> {
    @SuppressWarnings("unchecked")
    public final T[] singleOpcode = (T[]) new Object[(int) Byte.MAX_VALUE - (int) Byte.MIN_VALUE];
    public final HashMap<Integer, T> prefixOpcode = new HashMap<>();

    public T get(AbstractInsnNode insn) {
        if (insn instanceof PrefixInsnNode) {
            return prefixOpcode.get(((PrefixInsnNode) insn).intOpcode);
        } else {
            return singleOpcode[Byte.toUnsignedInt(insn.opcode)];
        }
    }

    public <E extends T> InsnMap<T> put(byte opcode, E value) {
        singleOpcode[Byte.toUnsignedInt(opcode)] = value;
        return this;
    }

    public <E extends T> InsnMap<T> put(byte[] opcodes, E value) {
        for (byte opcode : opcodes) {
            put(opcode, value);
        }
        return this;
    }

    public InsnMap<T> putInt(int opcode, T value) {
        prefixOpcode.put(opcode, value);
        return this;
    }

    public InsnMap<T> putInt(int[] opcodes, T value) {
        for (int opcode : opcodes) {
            putInt(opcode, value);
        }
        return this;
    }
}

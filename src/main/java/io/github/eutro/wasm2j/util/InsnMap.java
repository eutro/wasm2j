package io.github.eutro.wasm2j.util;

import io.github.eutro.jwasm.tree.AbstractInsnNode;
import io.github.eutro.jwasm.tree.PrefixInsnNode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

public class InsnMap<T> {
    @SuppressWarnings("unchecked")
    public final T[] singleOpcode = (T[]) new Object[(int) Byte.MAX_VALUE - (int) Byte.MIN_VALUE];
    public final HashMap<Integer, T> prefixOpcode = new HashMap<>();

    public T get(AbstractInsnNode insn) {
        if (insn instanceof PrefixInsnNode) {
            return getInt(((PrefixInsnNode) insn).intOpcode);
        } else {
            return getByte(insn.opcode);
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
        return values;
    }

    public T getByte(byte opcode) {
        return singleOpcode[Byte.toUnsignedInt(opcode)];
    }

    public T getInt(int opcode) {
        return prefixOpcode.get(opcode);
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

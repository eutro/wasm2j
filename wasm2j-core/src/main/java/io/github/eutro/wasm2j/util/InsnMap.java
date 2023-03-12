package io.github.eutro.wasm2j.util;

import io.github.eutro.jwasm.Opcodes;
import io.github.eutro.jwasm.tree.AbstractInsnNode;
import io.github.eutro.jwasm.tree.PrefixInsnNode;
import io.github.eutro.jwasm.tree.VectorInsnNode;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * A map from WebAssembly opcodes to values.
 * <p>
 * Values should not be null.
 *
 * @param <T> The type of value in the map.
 */
public class InsnMap<T> {
    /**
     * The mappings for single-byte opcodes.
     */
    @SuppressWarnings("unchecked")
    private final T[] singleOpcode = (T[]) new Object[(int) Byte.MAX_VALUE - (int) Byte.MIN_VALUE];
    /**
     * The mappings for {@link Opcodes#INSN_PREFIX}-prefixed instructions.
     */
    private final HashMap<Integer, T> prefixOpcode = new HashMap<>();
    /**
     * The mappings for {@link Opcodes#VECTOR_PREFIX}-prefixed instructions.
     */
    private final HashMap<Integer, T> vectorOpcode = new HashMap<>();

    /**
     * Get the mapping for the opcode of the given instruction.
     *
     * @param insn The instruction.
     * @return The mapping, or null if not present.
     */
    public T get(AbstractInsnNode insn) {
        if (insn instanceof PrefixInsnNode) {
            return getInt(((PrefixInsnNode) insn).intOpcode);
        } else if (insn instanceof VectorInsnNode) {
            return getVector(((VectorInsnNode) insn).intOpcode);
        } else {
            return getByte(insn.opcode);
        }
    }

    /**
     * Get the mapping for the given opcode.
     *
     * @param opcode    The first byte of the opcode.
     * @param intOpcode The LEB128 tail of the opcode, or zero if the instruction is one byte.
     * @return The mapping, or null if not present.
     */
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

    /**
     * Get all the values in the map.
     * <p>
     * This returns a new collection, not a view.
     *
     * @return All the values in the map.
     */
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

    /**
     * Look up the mapping for a single-byte opcode.
     *
     * @param opcode The opcode.
     * @return The mapping, or null if not present.
     */
    public T getByte(byte opcode) {
        return singleOpcode[Byte.toUnsignedInt(opcode)];
    }

    /**
     * Look up the mapping for a {@link Opcodes#INSN_PREFIX}-prefixed opcode.
     *
     * @param opcode The tail of the opcode.
     * @return The mapping, or null if not present.
     */
    public T getInt(int opcode) {
        return prefixOpcode.get(opcode);
    }

    /**
     * Look up the mapping for a {@link Opcodes#VECTOR_PREFIX}-prefixed opcode.
     *
     * @param intOpcode The tail of the opcode.
     * @return The mapping, or null if not present.
     */
    private T getVector(int intOpcode) {
        return vectorOpcode.get(intOpcode);
    }

    /**
     * Insert a mapping for the given opcode.
     *
     * @param opcode    The first byte of the opcode.
     * @param intOpcode The tail of the opcode, ignored if the first byte is not {@link Opcodes#INSN_PREFIX} or
     *                  {@link Opcodes#VECTOR_PREFIX}.
     * @param value     The mapping.
     */
    public void put(byte opcode, int intOpcode, @NotNull T value) {
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

    /**
     * Insert a mapping for the given single-byte opcode.
     *
     * @param opcode The opcode.
     * @param value  The mapping.
     */
    public void putByte(byte opcode, @NotNull T value) {
        singleOpcode[Byte.toUnsignedInt(opcode)] = value;
    }

    /**
     * Insert the same mapping for an array of opcodes.
     *
     * @param opcodes The opcodes.
     * @param value   The mapping.
     */
    public void putByte(byte[] opcodes, @NotNull T value) {
        for (byte opcode : opcodes) {
            putByte(opcode, value);
        }
    }

    /**
     * Insert a mapping for a {@link Opcodes#INSN_PREFIX}-prefixed opcode.
     *
     * @param opcode The tail of the opcode.
     * @param value  The mapping.
     */
    public void putInt(int opcode, @NotNull T value) {
        prefixOpcode.put(opcode, value);
    }

    /**
     * Insert the same mapping for an array of {@link Opcodes#INSN_PREFIX}-prefixed opcodes.
     *
     * @param opcodes The tails of the opcodes.
     * @param value   The mapping.
     */
    public void putInt(int[] opcodes, @NotNull T value) {
        for (int opcode : opcodes) {
            putInt(opcode, value);
        }
    }

    /**
     * Insert a mapping for a {@link Opcodes#VECTOR_PREFIX}-prefixed opcode.
     *
     * @param opcode The tail of the opcode.
     * @param value  The mapping.
     */
    public void putVector(int opcode, @NotNull T value) {
        vectorOpcode.put(opcode, value);
    }

    /**
     * Insert the same mapping for an array of {@link Opcodes#VECTOR_PREFIX}-prefixed opcodes.
     *
     * @param opcodes The tails of the opcodes.
     * @param value   The mapping.
     */
    public void putVector(int[] opcodes, @NotNull T value) {
        for (int opcode : opcodes) {
            putVector(opcode, value);
        }
    }
}

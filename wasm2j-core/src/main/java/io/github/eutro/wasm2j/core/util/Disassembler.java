package io.github.eutro.wasm2j.core.util;

import io.github.eutro.jwasm.attrs.InsnAttributes;
import io.github.eutro.jwasm.attrs.Opcode;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * A class with utilities for disassembling WebAssembly or Java bytecode.
 */
public class Disassembler {
    /**
     * The map of Java opcodes to their names.
     */
    private static Map<Integer, String> opcodeMnemonics = null;

    /**
     * Look up the mnemonic for a Java opcode.
     * <p>
     * Note: This is the name of the last static final field in {@link Opcodes} which has the value.
     *
     * @param opcode The Java opcode.
     * @return The mnemonic, or null if not found.
     */
    @Nullable
    public static String getMnemonic(int opcode) {
        if (opcodeMnemonics == null) generateMnemonics();
        return opcodeMnemonics.get(opcode);
    }

    /**
     * Look up the mnemonic for a WebAssembly instruction.
     *
     * @param byteOpcode The first byte of the instruction.
     * @param intOpcode Zero if the instruction is one byte,
     *                  otherwise the LEB128 decoding of the tail of the opcode.
     * @return The mnemonic of the instruction, or null if not found.
     */
    @Nullable
    public static String getWasmMnemonic(byte byteOpcode, int intOpcode) {
        InsnAttributes attrs = InsnAttributes.lookup(new Opcode(byteOpcode, intOpcode));
        return attrs == null ? null : attrs.getMnemonic();
    }

    /**
     * Disassemble a list of Java instructions.
     *
     * @param insns The list of Java instructions.
     * @return The disassembly of the instruction list.
     */
    public static String disassembleList(InsnList insns) {
        StringBuilder sb = new StringBuilder();
        for (AbstractInsnNode insn : insns) {
            disassembleInsn(sb, insn);
            if (insn != insns.getLast()) sb.append("; ");
        }
        return sb.toString();
    }

    /**
     * Disassemble a single Java instruction into a {@link StringBuilder}.
     *
     * @param sb The {@link StringBuilder} to output to.
     * @param insn The instruction to disassemble.
     */
    public static void disassembleInsn(StringBuilder sb, AbstractInsnNode insn) {
        sb.append(getMnemonic(insn.getOpcode()));
        for (Field field : insn.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            Object value;
            try {
                value = field.get(insn);
            } catch (IllegalAccessException e) {
                // should be impossible, all the fields returned
                // by getFields() should be accessible.
                throw new IllegalStateException(e);
            }
            if (value == null) continue;
            sb.append(' ')
                    .append(field.getName())
                    .append('=')
                    .append(value);
        }
    }

    /**
     * Disassemble Java instruction to a {@link String}.
     *
     * @param insn The instruction to disassemble.
     * @return The disassembled instruction.
     */
    public static String disassembleInsn(AbstractInsnNode insn) {
        StringBuilder sb = new StringBuilder();
        disassembleInsn(sb, insn);
        return sb.toString();
    }

    /**
     * Generate the table of Java mnemonics, for use by {@link #getMnemonic(int)}.
     */
    private static void generateMnemonics() {
        opcodeMnemonics = new HashMap<>();
        try {
            Field[] fields = Opcodes.class.getFields();
            for (Field field : fields) {
                int mods = field.getModifiers();
                if (!(Modifier.isStatic(mods) && Modifier.isFinal(mods)
                        && field.getType() == int.class)) continue;
                opcodeMnemonics.put((Integer) field.get(null), field.getName());
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }
}

package io.github.eutro.wasm2j.util;

import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

public class Disassembler {
    private static Map<Integer, String> opcodeMnemonics = null;

    private static InsnMap<String> wasmMnemonics = null;

    @Nullable
    public static String getMnemonic(int opcode) {
        if (opcodeMnemonics == null) generateMnemonics();
        return opcodeMnemonics.get(opcode);
    }

    @Nullable
    public static String getWasmMnemonic(byte byteOpcode, int intOpcode) {
        if (wasmMnemonics == null) generateWasmMnemonics();
        return byteOpcode == io.github.eutro.jwasm.Opcodes.INSN_PREFIX
                ? wasmMnemonics.getInt(intOpcode)
                : wasmMnemonics.getByte(byteOpcode);
    }

    public static String disassembleList(InsnList insns) {
        StringBuilder sb = new StringBuilder();
        for (AbstractInsnNode insn : insns) {
            disassembleInsn(sb, insn);
            if (insn != insns.getLast()) sb.append("; ");
        }
        return sb.toString();
    }

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
                throw new AssertionError();
            }
            if (value == null) continue;
            sb.append(' ')
                    .append(field.getName())
                    .append('=')
                    .append(value);
        }
    }

    public static String disassembleInsn(AbstractInsnNode insn) {
        StringBuilder sb = new StringBuilder();
        disassembleInsn(sb, insn);
        return sb.toString();
    }

    private static void generateMnemonics() {
        opcodeMnemonics = new HashMap<>();
        try {
            Field[] fields = Opcodes.class.getFields();
            for (Field field : fields) {
                if (field.getType() == int.class) {
                    opcodeMnemonics.put((Integer) field.get(null), field.getName());
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static void generateWasmMnemonics() {
        wasmMnemonics = new InsnMap<>();
        try {
            Field[] fields = io.github.eutro.jwasm.Opcodes.class.getFields();
            int i = 0;
            for (; i < fields.length; i++) {
                if (fields[i].getName().equals("INSN_PREFIX")) break;
            }
            for (; i < fields.length; i++) {
                Field field = fields[i];
                if (field.getType() == byte.class) {
                    wasmMnemonics.put((byte) field.get(null), field.getName());
                } else if (field.getType() == int.class) {
                    wasmMnemonics.putInt((int) field.get(null), field.getName());
                }
            }
        } catch (ReflectiveOperationException ignored) {
        }
    }
}

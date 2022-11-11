package io.github.eutro.wasm2j.util;

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

public class Disassembler {
    private static Map<Integer, String> opcodeMnemonics = null;

    @Nullable
    public static String getMnemonic(int opcode) {
        if (opcodeMnemonics == null) generateMnemonics();
        return opcodeMnemonics.get(opcode);
    }

    @Nullable
    public static String getWasmMnemonic(byte byteOpcode, int intOpcode) {
        InsnAttributes attrs = InsnAttributes.lookup(new Opcode(byteOpcode, intOpcode));
        return attrs == null ? null : attrs.getMnemonic();
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
}

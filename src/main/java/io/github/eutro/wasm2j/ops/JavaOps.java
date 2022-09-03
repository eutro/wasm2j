package io.github.eutro.wasm2j.ops;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.util.Disassembler;
import io.github.eutro.wasm2j.ext.JavaExts.*;
import org.intellij.lang.annotations.Language;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

public class JavaOps {
    public static final UnaryOpKey</* name */ String> INTRINSIC = new UnaryOpKey<>("intr");

    public static final SimpleOpKey TABLESWITCH = new SimpleOpKey("tableswitch");

    public static final Op THIS = new SimpleOpKey("this").create();

    public static final UnaryOpKey<JavaMethod> INVOKE = new UnaryOpKey<>("invoke");

    public static final UnaryOpKey<JavaField> GET_FIELD = new UnaryOpKey<>("get");
    public static final UnaryOpKey<JavaField> PUT_FIELD = new UnaryOpKey<>("put");

    public static final UnaryOpKey<JumpType> BR_COND = new UnaryOpKey<>("br_cond");
    public static final UnaryOpKey<JumpType> SELECT = new UnaryOpKey<>("select");
    public static final UnaryOpKey<JumpType> BOOL_SELECT = new UnaryOpKey<>("bool");

    public static final UnaryOpKey<MemoryType> MEM_GET = new UnaryOpKey<>("mem_get");
    public static final UnaryOpKey<MemoryType> MEM_SET = new UnaryOpKey<>("mem_set");

    public static final SimpleOpKey ARRAY_GET = new SimpleOpKey("array_get");
    public static final SimpleOpKey ARRAY_SET = new SimpleOpKey("array_set");

    public static final UnaryOpKey<InsnList> INSNS = new UnaryOpKey<>("insns", insns -> {
        StringBuilder sb = new StringBuilder();
        for (AbstractInsnNode insn : insns) {
            Disassembler.disassembleInsn(sb, insn);
            if (insn != insns.getLast()) sb.append("; ");
        }
        return sb.toString();
    });

    public enum JumpType {
        IFNE(Opcodes.IFNE),
        IFEQ(Opcodes.IFEQ),
        IFLT(Opcodes.IFLT),
        IFGE(Opcodes.IFGE),
        IFGT(Opcodes.IFGT),
        IFLE(Opcodes.IFLE),
        IFNULL(Opcodes.IFNULL),
        IF_ICMPEQ(Opcodes.IF_ICMPEQ),
        IF_ICMPNE(Opcodes.IF_ICMPNE),
        IF_ICMPLT(Opcodes.IF_ICMPLT),
        IF_ICMPGE(Opcodes.IF_ICMPGE),
        IF_ICMPGT(Opcodes.IF_ICMPGT),
        IF_ICMPLE(Opcodes.IF_ICMPLE),
        IF_ACMPEQ(Opcodes.IF_ACMPEQ),
        IF_ACMPNE(Opcodes.IF_ACMPNE),
        ;

        public final int opcode;

        JumpType(int opcode) {
            this.opcode = opcode;
        }

        public static JumpType fromOpcode(int opcode) {
            // @formatter:off
            switch (opcode) {
                case Opcodes.IFNE: return IFNE;
                case Opcodes.IFEQ: return IFEQ;
                case Opcodes.IFLT: return IFLT;
                case Opcodes.IFGE: return IFGE;
                case Opcodes.IFGT: return IFGT;
                case Opcodes.IFLE: return IFLE;
                case Opcodes.IFNULL: return IFNULL;
                case Opcodes.IF_ICMPEQ: return IF_ICMPEQ;
                case Opcodes.IF_ICMPNE: return IF_ICMPNE;
                case Opcodes.IF_ICMPLT: return IF_ICMPLT;
                case Opcodes.IF_ICMPGE: return IF_ICMPGE;
                case Opcodes.IF_ICMPGT: return IF_ICMPGT;
                case Opcodes.IF_ICMPLE: return IF_ICMPLE;
                case Opcodes.IF_ACMPEQ: return IF_ACMPEQ;
                case Opcodes.IF_ACMPNE: return IF_ACMPNE;
                default:
                    throw new IllegalArgumentException();
            }
            // @formatter:on
        }
    }

    public enum MemoryType {
        BYTE("get", "put"),
        SHORT("getShort", "putShort"),
        INT("getInt", "putInt"),
        LONG("getLong", "putLong"),
        FLOAT("getFloat", "putFloat"),
        DOUBLE("getDouble", "putDouble"),
        ;

        public final String get;
        public final String put;

        MemoryType(
                @Language(
                        value = "JAVA",
                        prefix = "class X{{ByteBuffer.wrap(new byte[0]).",
                        suffix = "(0);}}"
                )
                String get,
                @Language(
                        value = "JAVA",
                        prefix = "class X{{ByteBuffer.wrap(new byte[0]).",
                        suffix = "(0, (byte) 0);}}"
                )
                String put
        ) {
            this.get = get;
            this.put = put;
        }
    }

    static {
        for (OpKey key : new OpKey[] {
                THIS.key,
                GET_FIELD, // target is assumed non-null
                SELECT,
                BOOL_SELECT,
        }) {
            key.attachExt(CommonExts.IS_PURE, true);
        }
    }
}

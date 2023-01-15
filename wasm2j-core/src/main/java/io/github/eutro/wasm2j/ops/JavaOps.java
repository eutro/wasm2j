package io.github.eutro.wasm2j.ops;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.JavaExts.Handlable;
import io.github.eutro.wasm2j.ext.JavaExts.JavaField;
import io.github.eutro.wasm2j.ext.JavaExts.JavaMethod;
import io.github.eutro.wasm2j.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.util.Disassembler;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;

import static io.github.eutro.wasm2j.ext.CommonExts.markPure;

public class JavaOps {
    public static final UnaryOpKey<IntrinsicImpl> INTRINSIC = new UnaryOpKey<>("intr");

    public static final SimpleOpKey TABLESWITCH = new SimpleOpKey("tableswitch");
    public static final UnaryOpKey<int[]> LOOKUPSWITCH = new UnaryOpKey<>("lookupswitch");

    // control instruction to introduce a try block, "jumps" to the first target in the case of an exception
    public static final UnaryOpKey<Type> TRY = new UnaryOpKey<>("try");
    // must be the first instruction in a catch block (since it pops the exn off the stack)
    public static final UnaryOpKey<Type> CATCH = new UnaryOpKey<>("catch");

    public static final Op THIS = new SimpleOpKey("this").create();

    public static final UnaryOpKey<Handlable> HANDLE_OF = new UnaryOpKey<>("handle_of");
    public static final UnaryOpKey<JavaMethod> INVOKE = new UnaryOpKey<>("invoke");

    public static final UnaryOpKey<JavaField> GET_FIELD = new UnaryOpKey<>("get_field");
    public static final UnaryOpKey<JavaField> PUT_FIELD = new UnaryOpKey<>("put_field");

    public static final UnaryOpKey<JumpType> BR_COND = new UnaryOpKey<>("br_cond"); /* takenB fallthroughB */
    public static final UnaryOpKey<JumpType> SELECT = new UnaryOpKey<>("select"); /* cond taken fallthrough */
    public static final UnaryOpKey<JumpType> BOOL_SELECT = new UnaryOpKey<>("bool");

    public static final SimpleOpKey ARRAY_GET = new SimpleOpKey("array_get");
    public static final SimpleOpKey ARRAY_SET = new SimpleOpKey("array_set");

    public static final UnaryOpKey<InsnList> INSNS = new UnaryOpKey<>("insns", Disassembler::disassembleList);

    public static Op insns(InsnList insns) {
        return INSNS.create(insns);
    }

    public static Op insns(AbstractInsnNode... in) {
        InsnList il = new InsnList();
        for (AbstractInsnNode node : in) {
            il.add(node);
        }
        return insns(il);
    }

    public enum JumpType {
        IFNE(Opcodes.IFNE, null, 1),
        IFEQ(Opcodes.IFEQ, IFNE, 1),
        IFLT(Opcodes.IFLT, null, 1),
        IFGE(Opcodes.IFGE, IFLT, 1),
        IFGT(Opcodes.IFGT, null, 1),
        IFLE(Opcodes.IFLE, IFGT, 1),
        IFNULL(Opcodes.IFNULL, null, 1),
        IFNONNULL(Opcodes.IFNONNULL, IFNULL, 1),
        IF_ICMPEQ(Opcodes.IF_ICMPEQ, null, 2),
        IF_ICMPNE(Opcodes.IF_ICMPNE, IF_ICMPEQ, 2),
        IF_ICMPLT(Opcodes.IF_ICMPLT, null, 2),
        IF_ICMPGE(Opcodes.IF_ICMPGE, IF_ICMPLT, 2),
        IF_ICMPGT(Opcodes.IF_ICMPGT, null, 2),
        IF_ICMPLE(Opcodes.IF_ICMPLE, IF_ICMPGT, 2),
        IF_ACMPEQ(Opcodes.IF_ACMPEQ, null, 2),
        IF_ACMPNE(Opcodes.IF_ACMPNE, IF_ACMPEQ, 2),
        ;

        public final int opcode;
        public JumpType inverse;
        public final int arity;

        JumpType(int opcode, JumpType inverse, int arity) {
            this.opcode = opcode;
            this.inverse = inverse;
            this.arity = arity;
            if (inverse != null) {
                inverse.inverse = this;
            }
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

        @Nullable
        public JumpType combine(JumpType bool) {
            switch (this) {
                case IFNE:
                    return bool.inverse;
                case IFEQ:
                    return bool;
                default:
                    return null;
            }
        }
    }

    public static Op IADD = markPure(insns(new InsnNode(Opcodes.IADD)));
    public static Op LADD = markPure(insns(new InsnNode(Opcodes.LADD)));
    public static Op ISUB = markPure(insns(new InsnNode(Opcodes.ISUB)));
    public static Op IMUL = markPure(insns(new InsnNode(Opcodes.IMUL)));
    public static Op I2L = markPure(insns(new InsnNode(Opcodes.I2L)));
    public static Op I2L_U = markPure(INVOKE
            .create(JavaMethod.fromJava(Integer.class, "toUnsignedLong", int.class)));
    public static Op L2I_EXACT = markPure(INVOKE
            .create(JavaMethod.fromJava(Math.class, "toIntExact", long.class)));

    static {
        for (OpKey key : new OpKey[]{
                THIS.key,
                GET_FIELD, // target is assumed non-null
                SELECT,
                BOOL_SELECT,
        }) {
            key.attachExt(CommonExts.IS_PURE, true);
        }
    }
}

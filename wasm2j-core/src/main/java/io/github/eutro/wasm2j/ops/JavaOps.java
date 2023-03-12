package io.github.eutro.wasm2j.ops;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ssa.JClass.Handleable;
import io.github.eutro.wasm2j.ssa.JClass.JavaField;
import io.github.eutro.wasm2j.ssa.JClass.JavaMethod;
import io.github.eutro.wasm2j.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.ssa.JClass;
import io.github.eutro.wasm2j.ssa.Var;
import io.github.eutro.wasm2j.util.Disassembler;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;

import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;

import static io.github.eutro.wasm2j.ext.CommonExts.*;

/**
 * A collection of {@link Op}s and {@link OpKey}s that exist only in Java IR.
 */
public class JavaOps {
    /**
     * Effect: an intrinsic instruction, implemented in Java code.
     */
    public static final UnaryOpKey<IntrinsicImpl> INTRINSIC = new UnaryOpKey<>("intr");

    /**
     * Control: an {@link Opcodes#TABLESWITCH} instruction.
     * With n-1 jump targets, the first n-1 correspond to keys {@code [0, n-1)}. The last
     * is the default branch.
     */
    public static final SimpleOpKey TABLESWITCH = new SimpleOpKey("tableswitch");
    /**
     * Control: an {@link Opcodes#LOOKUPSWITCH} instruction.
     * The argument corresponds to each jump target's key, with the last block the
     * default branch.
     */
    public static final UnaryOpKey<int[]> LOOKUPSWITCH = new UnaryOpKey<>("lookupswitch");

    /**
     * Control: introduce a try block, "jumps" to the first target in the case of an exception
     * in its second block.
     */
    public static final UnaryOpKey<Type> TRY = new UnaryOpKey<>("try");
    /**
     * Effect: pops the exception from the stack. Must be the first instruction in its block.
     */
    public static final UnaryOpKey<Type> CATCH = new UnaryOpKey<>("catch");

    /**
     * Effect: loads the implicit receiver of a method.
     */
    public static final Op THIS = new SimpleOpKey("this").create();
    /**
     * Effect: ignores its argument.
     */
    public static final Op DROP = new SimpleOpKey("drop").create();

    /**
     * Effect: returns a {@link MethodHandle method handle} to the given method or field getter/setter.
     */
    public static final UnaryOpKey<Handleable> HANDLE_OF = new UnaryOpKey<>("handle_of");
    /**
     * Effect: invokes the given method.
     */
    public static final UnaryOpKey<JavaMethod> INVOKE = new UnaryOpKey<>("invoke");

    /**
     * Effect: get the given field on its argument, or statically with no argument.
     */
    public static final UnaryOpKey<JavaField> GET_FIELD = new UnaryOpKey<>("get_field");
    /**
     * Effect: set the given field on its first argument, or statically with only one argument.
     */
    public static final UnaryOpKey<JavaField> PUT_FIELD = new UnaryOpKey<>("put_field");

    /**
     * Control: jump conditionally. The first target is the one taken if the condition holds, the second is the fallthrough.
     */
    public static final UnaryOpKey<JumpType> BR_COND = new UnaryOpKey<>("br_cond"); /* takenB fallthroughB */
    /**
     * Effect: check the third argument, if it is true, returns the first argument, otherwise the second.
     */
    public static final UnaryOpKey<JumpType> SELECT = new UnaryOpKey<>("select"); /* taken fallthrough cond... */
    /**
     * Effect: returns true if the condition holds, false otherwise.
     */
    public static final UnaryOpKey<JumpType> BOOL_SELECT = new UnaryOpKey<>("bool");

    /**
     * Effect: get an {@code array}'s {@code n}th element. Arguments in that order.
     */
    public static final SimpleOpKey ARRAY_GET = new SimpleOpKey("array_get");
    /**
     * Effect: set an {@code array}'s {@code n}th element to a {@code value}. Arguments in that order.
     */
    public static final SimpleOpKey ARRAY_SET = new SimpleOpKey("array_set");

    /**
     * Effect: an inline sequence of ASM instructions.
     */
    public static final UnaryOpKey<InsnList> INSNS = new UnaryOpKey<>("insns", Disassembler::disassembleList);

    /**
     * Construct an {@link #INSNS} operator for the given instruction list.
     *
     * @param insns The instruction list.
     * @return The operator.
     */
    public static Op insns(InsnList insns) {
        return INSNS.create(insns);
    }

    /**
     * Construct an {@link #INSNS} operator for the given instructions.
     *
     * @param in The instructions.
     * @return The operator.
     */
    public static Op insns(AbstractInsnNode... in) {
        InsnList il = new InsnList();
        for (AbstractInsnNode node : in) {
            il.add(node);
        }
        return insns(il);
    }

    /**
     * A conditional jump's type in Java bytecode. Each {@link JumpType} represents a condition,
     * the jump will be taken if the condition holds.
     */
    public enum JumpType {
        /**
         * Jumps if the argument integer is not zero.
         */
        IFNE(Opcodes.IFNE, null, 1),
        /**
         * Jumps if the argument integer is zero.
         */
        IFEQ(Opcodes.IFEQ, IFNE, 1),
        /**
         * Jumps if the argument integer is less than zero.
         */
        IFLT(Opcodes.IFLT, null, 1),
        /**
         * Jumps if the argument integer is greater than or equal to zero.
         */
        IFGE(Opcodes.IFGE, IFLT, 1),
        /**
         * Jumps if the argument integer is greater than zero.
         */
        IFGT(Opcodes.IFGT, null, 1),
        /**
         * Jumps if the argument integer is less than or equal to zero.
         */
        IFLE(Opcodes.IFLE, IFGT, 1),
        /**
         * Jumps if the argument is null.
         */
        IFNULL(Opcodes.IFNULL, null, 1),
        /**
         * Jumps if the argument is not null.
         */
        IFNONNULL(Opcodes.IFNONNULL, IFNULL, 1),
        /**
         * Jumps if the first argument is equal to the second, which are both integers.
         */
        IF_ICMPEQ(Opcodes.IF_ICMPEQ, null, 2),
        /**
         * Jumps if the first argument is not equal to the second, which are both integers.
         */
        IF_ICMPNE(Opcodes.IF_ICMPNE, IF_ICMPEQ, 2),
        /**
         * Jumps if the first argument is less than the second, which are both integers.
         */
        IF_ICMPLT(Opcodes.IF_ICMPLT, null, 2),
        /**
         * Jumps if the first argument is greater than or equal to the second, which are both integers.
         */
        IF_ICMPGE(Opcodes.IF_ICMPGE, IF_ICMPLT, 2),
        /**
         * Jumps if the first argument is greater than the second, which are both integers.
         */
        IF_ICMPGT(Opcodes.IF_ICMPGT, null, 2),
        /**
         * Jumps if the first argument is less than or equal to the second, which are both integers.
         */
        IF_ICMPLE(Opcodes.IF_ICMPLE, IF_ICMPGT, 2),
        /**
         * Jumps if the first argument is referentially equal to the second, which are both references.
         */
        IF_ACMPEQ(Opcodes.IF_ACMPEQ, null, 2),
        /**
         * Jumps if the first argument is referentially not equal to the second, which are both references.
         */
        IF_ACMPNE(Opcodes.IF_ACMPNE, IF_ACMPEQ, 2),
        ;

        /**
         * The Java opcode of the jump instruction.
         */
        public final int opcode;
        /**
         * The inverse of the condition.
         */
        public JumpType inverse;
        /**
         * The number of arguments to the instruction.
         */
        public final int arity;

        JumpType(int opcode, JumpType inverse, int arity) {
            this.opcode = opcode;
            this.inverse = inverse;
            this.arity = arity;
            if (inverse != null) {
                inverse.inverse = this;
            }
        }

        /**
         * Get the jump type for a specific Java opcode.
         *
         * @param opcode The opcode.
         * @return The jump type.
         */
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

        /**
         * Combine another condition with this one, if possible. This is the XAND of their results.
         *
         * @param bool The condition.
         * @return The combined condition, or null if it was not possible to combine them.
         */
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

    /**
     * Effect: integer addition (marked as pure)
     */
    public static final Op IADD = markPure(insns(new InsnNode(Opcodes.IADD)));
    /**
     * Effect: integer subtraction (marked as pure)
     */
    public static final Op ISUB = markPure(insns(new InsnNode(Opcodes.ISUB)));
    /**
     * Effect: long addition (marked as pure)
     */
    public static final Op LADD = markPure(insns(new InsnNode(Opcodes.LADD)));
    /**
     * Effect: integer multiplication (marked as pure)
     */
    public static final Op IMUL = markPure(insns(new InsnNode(Opcodes.IMUL)));
    /**
     * Effect: sign-extending integer to long conversion (marked as pure)
     */
    public static final Op I2L = markPure(insns(new InsnNode(Opcodes.I2L)));
    /**
     * Effect: zero-extending integer to long conversion (marked as pure)
     */
    public static final Op I2L_U;
    /**
     * Effect: unsigned integer division (not pure)
     */
    public static final Op IDIV_U;

    /**
     * Effect: long to integer conversion, trapping on failure
     */
    public static final Op L2I_EXACT = INVOKE
            .create(JClass.emptyFromJava(Math.class).lookupMethod("toIntExact", long.class));

    static {
        JClass integerClass = JClass.emptyFromJava(Integer.class);
        I2L_U = markPure(INVOKE.create(integerClass.lookupMethod("toUnsignedLong", int.class)));
        IDIV_U = INVOKE.create(integerClass.lookupMethod("divideUnsigned", int.class, int.class));
    }

    static {
        IADD.attachExt(CONSTANT_PROPAGATOR, insn -> {
            int constant = 0;
            for (Var arg : insn.args()) {
                Object value = arg.getNullable(CommonExts.CONSTANT_VALUE);
                if (value == null) return insn;
                constant += (int) value;
            }
            return CommonOps.constant(constant);
        });
        ISUB.attachExt(CONSTANT_PROPAGATOR, insn -> {
            Object lhs = insn.args().get(0).getNullable(CONSTANT_VALUE);
            if (lhs == null) return insn;
            Object rhs = insn.args().get(1).getNullable(CONSTANT_VALUE);
            if (rhs == null) return insn;
            return CommonOps.constant((int) lhs - (int) rhs);
        });
        IMUL.attachExt(CONSTANT_PROPAGATOR, insn -> {
            int constant = 1;
            for (Var arg : insn.args()) {
                Object value = arg.getNullable(CommonExts.CONSTANT_VALUE);
                if (value == null) return insn;
                constant *= (int) value;
            }
            return CommonOps.constant(constant);
        });
        LADD.attachExt(CONSTANT_PROPAGATOR, insn -> {
            long constant = 0;
            for (Var arg : insn.args()) {
                Object value = arg.getNullable(CommonExts.CONSTANT_VALUE);
                if (value == null) return insn;
                constant += (long) value;
            }
            return CommonOps.constant(constant);
        });
        I2L.attachExt(CONSTANT_PROPAGATOR, insn -> {
            Object constValue = insn.args().get(0).getNullable(CONSTANT_VALUE);
            if (constValue == null) return insn;
            return CommonOps.constant((long) (int) constValue);
        });
        I2L_U.attachExt(CONSTANT_PROPAGATOR, insn -> {
            Object constValue = insn.args().get(0).getNullable(CONSTANT_VALUE);
            if (constValue == null) return insn;
            return CommonOps.constant(Integer.toUnsignedLong((int) constValue));
        });
        L2I_EXACT.attachExt(CONSTANT_PROPAGATOR, insn -> {
            Object constValue = insn.args().get(0).getNullable(CONSTANT_VALUE);
            if (constValue == null) return insn;
            long longValue = (long) constValue;
            if (longValue != (int) longValue) return insn;
            return CommonOps.constant((int) longValue);
        });
        INTRINSIC.attachExt(CONSTANT_PROPAGATOR, insn -> {
            IntrinsicImpl intr = JavaOps.INTRINSIC.cast(insn.op).arg;
            if (intr.eval == null) return insn;
            for (Var arg : insn.args()) {
                if (arg.getNullable(CONSTANT_VALUE) == null) return insn;
            }
            List<Object> args = new ArrayList<>();
            for (Var arg : insn.args()) {
                args.add(takeNull(arg.getNullable(CONSTANT_VALUE)));
            }
            try {
                Object res = intr.eval.invokeWithArguments(args);
                return CommonOps.constant(fillNull(res));
            } catch (Throwable ignored) {
                return insn;
            }
        });
    }

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

package io.github.eutro.wasm2j.ops;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Insn;
import io.github.eutro.wasm2j.ssa.Var;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

import static io.github.eutro.wasm2j.ext.CommonExts.takeNull;

/**
 * A collection of {@link Op}s and {@link OpKey}s that exist in both WebAssembly and Java IR.
 */
public class CommonOps {
    /**
     * Control: an unconditional jump.
     */
    public static final Op BR = new SimpleOpKey("br").create();
    /**
     * Control: returns from the function.
     */
    public static final Op RETURN = new SimpleOpKey("return").create();
    /**
     * Control: traps, (Java: throws an exception) with the given message, returns nothing
     */
    public static final UnaryOpKey<String> TRAP = new UnaryOpKey<>("trap");

    /**
     * Effect: returns its argument(s)
     */
    public static final Op IDENTITY = new SimpleOpKey("id").create();

    /**
     * Effect: returns the argument corresponding to its predecessor
     * <p>
     * Must precede any other (non-phi) effect instructions within its basic block.
     */
    public static final UnaryOpKey<List<BasicBlock>> PHI = new UnaryOpKey<>("phi", bbs ->
            bbs.stream().map(BasicBlock::toTargetString).collect(Collectors.joining(" ")));

    /**
     * Effect: returns the {@code n}th argument of the function.
     */
    public static final UnaryOpKey<Integer> ARG = new UnaryOpKey<>("arg");
    /**
     * Effect: returns the constant.
     */
    public static final UnaryOpKey<Object> CONST = new UnaryOpKey<>("const").allowNull();

    static {
        for (OpKey key : new OpKey[]{
                IDENTITY.key,
                PHI,
                ARG,
                CONST,
        }) {
            key.attachExt(CommonExts.IS_PURE, true);
        }
        IDENTITY.attachExt(CommonExts.CONSTANT_PROPAGATOR, insn -> {
            if (insn.args().size() == 1) {
                Var arg = insn.args().get(0);
                Object maybeValue = arg.getNullable(CommonExts.CONSTANT_VALUE);
                if (maybeValue == null) return insn;
                return constant(takeNull(maybeValue));
            }
            return insn;
        });
    }

    /**
     * Return a constant instruction which returns {@code k}.
     *
     * @param k The constant.
     * @return The instruction.
     */
    public static Insn constant(Object k) {
        return CONST.create(k).insn();
    }

    /**
     * Check quickly whether the assignment of {@code var} is an
     * {@link #CONST} instruction with the value {@code k}.
     *
     * @param var The variable.
     * @param k The constant.
     * @return The above.
     */
    public static boolean quickCheckConstant(Var var, @NotNull Object k) {
        return k.equals(CommonOps.CONST.argNullable(var.getExtOrThrow(CommonExts.ASSIGNED_AT).insn().op));
    }
}

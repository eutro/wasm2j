package io.github.eutro.wasm2j.core.ops;

import io.github.eutro.wasm2j.core.ssa.Effect;
import io.github.eutro.wasm2j.core.ssa.Var;
import io.github.eutro.wasm2j.core.ext.DelegatingExtHolder;
import io.github.eutro.wasm2j.core.ext.ExtContainer;
import io.github.eutro.wasm2j.core.ssa.Insn;

import java.util.List;

/**
 * An operation, encapsulating an {@link OpKey operation key} and any intermediates.
 */
public /* virtual */ class Op extends DelegatingExtHolder {
    /**
     * The key of the operation.
     */
    public final OpKey key;

    /**
     * Construct an operation with the given key.
     *
     * @param key The key.
     */
    protected Op(OpKey key) {
        this.key = key;
    }

    @Override
    protected ExtContainer getDelegate() {
        return key;
    }

    @Override
    public String toString() {
        return key.toString();
    }

    /**
     * Construct an instruction with this as its operation, applied to the given arguments.
     *
     * @param vars The arguments.
     * @return The instruction.
     */
    public Insn insn(Var... vars) {
        return new Insn(this, vars);
    }

    /**
     * Construct an instruction with this as its operation, applied to the given arguments.
     *
     * @param vars The arguments.
     * @return The instruction.
     */
    public Insn insn(List<Var> vars) {
        return new Insn(this, vars);
    }

    /**
     * Construct an instruction with this as its operation, by copying the arguments from
     * an instruction.
     *
     * @param insn The instruction to copy from.
     * @return The new instruction.
     */
    public Insn copyFrom(Insn insn) {
        return insn(insn.args());
    }

    /**
     * Construct an effect with this as its operation, by copying the arguments
     * and assignees from an effect.
     *
     * @param fx The effect to copy from.
     * @return The new effect.
     */
    public Effect copyFrom(Effect fx) {
        return copyFrom(fx.insn()).copyFrom(fx);
    }
}

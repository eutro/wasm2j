package io.github.eutro.wasm2j.ops;

import io.github.eutro.wasm2j.ext.DelegatingExtHolder;
import io.github.eutro.wasm2j.ext.ExtContainer;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.Insn;
import io.github.eutro.wasm2j.ssa.Var;

import java.util.List;

/**
 * An operation, encapsulating an {@link OpKey operation key} and any intermediates.
 */
public /* virtual */ class Op extends DelegatingExtHolder {
    public OpKey key;

    public Op(OpKey key) {
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

    public Insn insn(Var... vars) {
        return new Insn(this, vars);
    }

    public Insn insn(List<Var> vars) {
        return new Insn(this, vars);
    }

    public Insn copyFrom(Insn insn) {
        return insn(insn.args());
    }

    public Effect copyFrom(Effect fx) {
        return copyFrom(fx.insn()).copyFrom(fx);
    }
}

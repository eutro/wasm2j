package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.DelegatingExtHolder;
import io.github.eutro.wasm2j.ext.ExtContainer;
import io.github.eutro.wasm2j.ops.Op;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class Insn extends DelegatingExtHolder {
    public static boolean TRACK_INSN_CREATIONS = System.getenv("WASM2J_TRACK_INSN_CREATIONS") != null;

    public Throwable created = TRACK_INSN_CREATIONS ? new Throwable("constructed") : null;
    public Op op;
    public List<Var> args;

    public Insn(Op op, List<Var> args) {
        this.op = op;
        this.args = new ArrayList<>(args);
    }

    public Insn(Op op, Var... args) {
        this(op, Arrays.asList(args));
    }

    @Override
    protected ExtContainer getDelegate() {
        return op;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(op);
        for (Var arg : args) {
            sb.append(' ').append(arg);
        }
        return sb.toString();
    }

    public Effect assignTo(Var... vars) {
        return new Effect(new ArrayList<>(Arrays.asList(vars)), this);
    }

    public Effect assignTo(List<Var> vars) {
        return new Effect(vars, this);
    }

    public Control jumpsTo(BasicBlock... targets) {
        return jumpsTo(Arrays.asList(targets));
    }

    public Control jumpsTo(List<BasicBlock> targets) {
        return new Control(this, targets);
    }

    public Effect copyFrom(Effect fx) {
        return assignTo(fx.getAssignsTo());
    }
}

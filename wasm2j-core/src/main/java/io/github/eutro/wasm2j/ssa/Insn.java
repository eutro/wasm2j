package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.DelegatingExtHolder;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ext.ExtContainer;
import io.github.eutro.wasm2j.ops.Op;
import org.jetbrains.annotations.Nullable;

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
        ArrayList<Var> al = new ArrayList<>(args);
        al.trimToSize();
        this.args = al;
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

    // exts
    private Object owner = null;

    @SuppressWarnings("unchecked")
    @Override
    public <T> @Nullable T getNullable(Ext<T> ext) {
        if (ext == CommonExts.OWNING_EFFECT) {
            return owner instanceof Effect ? (T) owner : null;
        } else if (ext == CommonExts.OWNING_CONTROL) {
            return owner instanceof Control ? (T) owner : null;
        }
        return super.getNullable(ext);
    }

    @Override
    public <T> void attachExt(Ext<T> ext, T value) {
        if (ext == CommonExts.OWNING_EFFECT || ext == CommonExts.OWNING_CONTROL) {
            owner = value;
            return;
        }
        super.attachExt(ext, value);
    }

    @Override
    public <T> void removeExt(Ext<T> ext) {
        if (ext == CommonExts.OWNING_EFFECT || ext == CommonExts.OWNING_CONTROL) {
            owner = null;
            return;
        }
        super.removeExt(ext);
    }
}

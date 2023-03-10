package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.*;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class Effect extends DelegatingExtHolder {
    private final TrackedList<Var> assignsTo = new TrackedList<Var>(null) {
        @Override
        protected void onAdded(Var elt) {
            elt.attachExt(CommonExts.ASSIGNED_AT, Effect.this);
        }

        @Override
        protected void onRemoved(Var elt) {
        }
    };
    private Insn insn;

    public Effect(List<Var> assignsTo, Insn insn) {
        this.setAssignsTo(assignsTo);
        this.setInsn(insn);
    }

    @Override
    protected ExtContainer getDelegate() {
        return insn();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        switch (getAssignsTo().size()) {
            case 0:
                break;
            case 1:
                sb.append(getAssignsTo().get(0)).append(" = ");
                break;
            default:
                sb.append(getAssignsTo().stream()
                        .map(Objects::toString)
                        .collect(Collectors.joining(", ", "", " = ")));
                break;
        }
        sb.append(insn());
        return sb.toString();
    }

    public List<Var> getAssignsTo() {
        return assignsTo;
    }

    public void setAssignsTo(List<Var> assignsTo) {
        this.assignsTo.setViewed(assignsTo);
    }

    public Insn insn() {
        return insn;
    }

    public void setInsn(Insn insn) {
        insn.attachExt(CommonExts.OWNING_EFFECT, this);
        this.insn = insn;
    }

    // exts
    private BasicBlock owner = null;

    @SuppressWarnings("unchecked")
    @Override
    public <T> @Nullable T getNullable(Ext<T> ext) {
        if (ext == CommonExts.OWNING_BLOCK) {
            return (T) owner;
        }
        return super.getNullable(ext);
    }

    @Override
    public <T> void attachExt(Ext<T> ext, T value) {
        if (ext == CommonExts.OWNING_BLOCK) {
            owner = (BasicBlock) value;
            return;
        }
        super.attachExt(ext, value);
    }

    @Override
    public <T> void removeExt(Ext<T> ext) {
        if (ext == CommonExts.OWNING_BLOCK) {
            owner = null;
            return;
        }
        super.removeExt(ext);
    }
}

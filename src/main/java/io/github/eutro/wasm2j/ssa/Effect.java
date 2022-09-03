package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.DelegatingExtHolder;
import io.github.eutro.wasm2j.ext.ExtContainer;
import io.github.eutro.wasm2j.ext.TrackedList;
import io.github.eutro.wasm2j.ext.*;

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
    public Insn insn;

    public Effect(List<Var> assignsTo, Insn insn) {
        this.setAssignsTo(assignsTo);
        this.insn = insn;
    }

    @Override
    protected ExtContainer getDelegate() {
        return insn;
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
        sb.append(insn);
        return sb.toString();
    }

    public List<Var> getAssignsTo() {
        return assignsTo;
    }

    public void setAssignsTo(List<Var> assignsTo) {
        this.assignsTo.setViewed(assignsTo);
    }
}

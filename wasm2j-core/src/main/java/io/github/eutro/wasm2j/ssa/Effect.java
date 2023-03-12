package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.*;
import org.jetbrains.annotations.Nullable;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * An effect, encapsulating an {@link Insn instruction}, and the
 * variables its results are assigned to.
 */
public final class Effect extends DelegatingExtHolder {
    // null if empty,
    // Var if unary,
    // Var[] otherwise.
    // Micro-optimising the size of this is worth it because there are millions of these.
    private Object assignsTo;
    private Insn insn;

    Effect(List<Var> assignsTo, Insn insn) {
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

    /**
     * Get the list of variables this effect assigns to.
     *
     * @return The list.
     */
    public List<Var> getAssignsTo() {
        return new AssignedTo();
    }

    /**
     * Set the list of variables this effect assigns to. The list will be copied.
     *
     * @param assignsTo The list of variables.
     */
    public void setAssignsTo(List<Var> assignsTo) {
        switch (assignsTo.size()) {
            case 0:
                this.assignsTo = null;
                break;
            case 1: {
                Var var = assignsTo.get(0);
                this.assignsTo = var;
                register(var);
                break;
            }
            default: {
                Var[] vars = assignsTo.toArray(new Var[0]);
                this.assignsTo = vars;
                for (Var var : vars) {
                    register(var);
                }
                break;
            }
        }
    }

    private void register(Var var) {
        var.attachExt(CommonExts.ASSIGNED_AT, this);
    }

    /**
     * Get the {@link Insn underlying instruction} of this effect instruction.
     *
     * @return The instruction.
     */
    public Insn insn() {
        return insn;
    }

    /**
     * Set the {@link Insn underlying instruction} of this effect instruction.
     *
     * @param insn The instruction.
     */
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

    private class AssignedTo extends AbstractList<Var> implements List<Var> {
        @Override
        public Var get(int index) {
            if (assignsTo == null) throw new IndexOutOfBoundsException();
            if (assignsTo instanceof Var) {
                if (index != 0) throw new IndexOutOfBoundsException();
                return (Var) assignsTo;
            }
            Var[] vars = (Var[]) assignsTo;
            return vars[index];
        }

        @Override
        public Var set(int index, Var value) {
            if (assignsTo == null) throw new IndexOutOfBoundsException();
            if (assignsTo instanceof Var) {
                if (index != 0) throw new IndexOutOfBoundsException();
                Var old = (Var) assignsTo;
                assignsTo = value;
                register(value);
                return old;
            }
            Var[] vars = (Var[]) assignsTo;
            Var old = vars[index];
            vars[index] = value;
            register(value);
            return old;
        }

        @Override
        public int size() {
            if (assignsTo == null) return 0;
            if (assignsTo instanceof Var) return 1;
            return ((Var[]) assignsTo).length;
        }
    }
}

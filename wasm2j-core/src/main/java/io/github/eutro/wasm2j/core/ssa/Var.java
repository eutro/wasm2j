package io.github.eutro.wasm2j.core.ssa;

import io.github.eutro.wasm2j.core.ext.CommonExts;
import io.github.eutro.wasm2j.core.ext.Ext;
import io.github.eutro.wasm2j.core.ext.ExtHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * A variable, a virtual register.
 * <p>
 * May be set more than once, but only if the IR is not in SSA form.
 */
public final class Var extends ExtHolder {
    /**
     * The name of the variable.
     */
    public final String name;
    /**
     * The index of the variable, to distinguish from
     * others with the same name in the same function,
     * if {@link Function#UNIQUE_VAR_NAMES counting is enabled}.
     */
    public final int index;

    Var(String name, int index) {
        this.name = name;
        this.index = index;
    }

    @Override
    public String toString() {
        return '$' + name + (index == 0 ? "" : "." + index);
    }

    // exts
    private Effect assignedAt = null;
    private Object constantValue = null;
    private Set<Insn> usedAt = null;

    @SuppressWarnings("unchecked")
    @Override
    public <T> @Nullable T getNullable(Ext<T> ext) {
        if (ext == CommonExts.ASSIGNED_AT) {
            return (T) assignedAt;
        }
        if (ext == CommonExts.CONSTANT_VALUE) {
            return (T) constantValue;
        }
        if (ext == CommonExts.USED_AT) {
            return (T) usedAt;
        }
        return super.getNullable(ext);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> void attachExt(Ext<T> ext, T value) {
        if (ext == CommonExts.ASSIGNED_AT) {
            assignedAt = (Effect) value;
            return;
        }
        if (ext == CommonExts.CONSTANT_VALUE) {
            constantValue = value;
            return;
        }
        if (ext == CommonExts.USED_AT) {
            usedAt = (Set<Insn>) value;
            return;
        }
        super.attachExt(ext, value);
    }

    @Override
    public <T> void removeExt(Ext<T> ext) {
        if (ext == CommonExts.ASSIGNED_AT) {
            assignedAt = null;
            return;
        }
        if (ext == CommonExts.CONSTANT_VALUE) {
            constantValue = null;
            return;
        }
        if (ext == CommonExts.USED_AT) {
            usedAt = null;
            return;
        }
        super.removeExt(ext);
    }
}

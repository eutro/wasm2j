package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ext.ExtHolder;
import org.jetbrains.annotations.Nullable;

public final class Var extends ExtHolder {
    public String name;
    public int index;

    public Var(String name, int index) {
        this.name = name;
        this.index = index;
    }

    @Override
    public String toString() {
        return '$' + name + (index == 0 ? "" : "." + index);
    }

    // exts
    private Effect assignedAt = null;

    @SuppressWarnings("unchecked")
    @Override
    public <T> @Nullable T getNullable(Ext<T> ext) {
        if (ext == CommonExts.ASSIGNED_AT) {
            return (T) assignedAt;
        }
        return super.getNullable(ext);
    }

    @Override
    public <T> void attachExt(Ext<T> ext, T value) {
        if (ext == CommonExts.ASSIGNED_AT) {
            assignedAt = (Effect) value;
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
        super.removeExt(ext);
    }
}

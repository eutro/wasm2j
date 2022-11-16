package io.github.eutro.wasm2j.ext;

import org.jetbrains.annotations.Nullable;

public abstract class DelegatingExtHolder extends ExtHolder {
    protected abstract ExtContainer getDelegate();

    @Override
    public <T> @Nullable T getNullable(Ext<T> ext) {
        T localExt = super.getNullable(ext);
        if (localExt != null) return localExt;
        return getDelegate().getNullable(ext);
    }
}

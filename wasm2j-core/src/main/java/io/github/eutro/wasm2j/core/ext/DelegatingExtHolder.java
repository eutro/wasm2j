package io.github.eutro.wasm2j.core.ext;

import org.jetbrains.annotations.Nullable;

/**
 * An {@link ExtHolder} that additionally delegates to another
 * {@link ExtContainer} if it can't find a given ext in itself.
 */
public abstract class DelegatingExtHolder extends ExtHolder {
    /**
     * Get the {@link ExtHolder} to delegate to.
     *
     * @return The delegate.
     */
    protected abstract ExtContainer getDelegate();

    @Override
    public <T> @Nullable T getNullable(Ext<T> ext) {
        T localExt = super.getNullable(ext);
        if (localExt != null) return localExt;
        ExtContainer delegate = getDelegate();
        if (delegate != null) return delegate.getNullable(ext);
        return null;
    }
}

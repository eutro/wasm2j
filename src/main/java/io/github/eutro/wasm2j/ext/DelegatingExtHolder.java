package io.github.eutro.wasm2j.ext;

import java.util.Optional;

public abstract class DelegatingExtHolder extends ExtHolder {
    protected abstract ExtContainer getDelegate();

    @Override
    public <T> Optional<T> getExt(Ext<T> ext) {
        Optional<T> localExt = super.getExt(ext);
        if (localExt.isPresent()) return localExt;
        return getDelegate().getExt(ext);
    }
}

package io.github.eutro.wasm2j.ext;

import java.util.Optional;

public class Ext<T> {
    private final Class<T> type;

    public Ext(Class<T> type) {
        this.type = type;
    }

    @SuppressWarnings("unchecked")
    public static <T, R extends T> Ext<R> create(Class<T> type) {
        return (Ext<R>) new Ext<>(type);
    }

    public Class<T> getType() {
        return type;
    }

    public Optional<T> getIn(ExtContainer ec) {
        return ec.getExt(this);
    }
}

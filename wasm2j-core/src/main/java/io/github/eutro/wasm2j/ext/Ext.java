package io.github.eutro.wasm2j.ext;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class Ext<T> implements Comparable<Ext<?>> {
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);

    private final Class<T> type;
    public final int id = ID_COUNTER.getAndIncrement();

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

    @Override
    public int compareTo(@NotNull Ext<?> o) {
        return Integer.compare(id, o.id);
    }

    @Override
    public int hashCode() {
        return id;
    }
}

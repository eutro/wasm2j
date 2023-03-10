package io.github.eutro.wasm2j.ext;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class Ext<T> implements Comparable<Ext<?>> {
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);

    private final Class<T> type;
    public final int id = ID_COUNTER.getAndIncrement();
    public final String name;

    public Ext(Class<T> type, String name) {
        this.type = type;
        this.name = name;
    }

    @SuppressWarnings("unchecked")
    public static <T, R extends T> Ext<R> create(Class<T> type, String name) {
        return (Ext<R>) new Ext<>(type, name);
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

    @Override
    public String toString() {
        return name + ": " + type.getName();
    }
}

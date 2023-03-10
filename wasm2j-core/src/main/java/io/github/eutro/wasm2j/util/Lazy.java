package io.github.eutro.wasm2j.util;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

public class Lazy<T> implements Supplier<T> {
    private Supplier<T> thunk;
    private T value;

    public Lazy(Supplier<T> thunk) {
        this.thunk = thunk;
    }

    public static <T> Lazy<T> lazy(Supplier<T> thunk) {
        return new Lazy<>(thunk);
    }

    @Override
    public T get() {
        if (thunk != null) {
            value = thunk.get();
            thunk = null;
        }
        return value;
    }

    public Lazy<T> mapInPlace(UnaryOperator<T> op) {
        if (thunk == null) {
            T value = this.value;
            this.value = null;
            thunk = () -> op.apply(value);
        } else {
            Supplier<T> thunk = this.thunk;
            this.thunk = () -> op.apply(thunk.get());
        }
        return this;
    }

    // for clearing a value no longer needed
    public void set(T value) {
        this.thunk = null;
        this.value = value;
    }
}

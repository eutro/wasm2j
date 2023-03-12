package io.github.eutro.wasm2j.core.util;

import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * A lazily-initialized value.
 *
 * @param <T> The type of the value.
 */
public final class Lazy<T> implements Supplier<T> {
    /**
     * The function creating the value.
     */
    private Supplier<T> thunk;
    /**
     * The value.
     */
    private T value;

    /**
     * Construct a lazy value from the thunk.
     *
     * @param thunk The thunk.
     */
    private Lazy(Supplier<T> thunk) {
        this.thunk = thunk;
    }

    /**
     * Create a lazily-initialized value.
     *
     * @param thunk The function that produces the value.
     * @param <T>   The type of the value.
     * @return The lazily-initialized value.
     */
    public static <T> Lazy<T> lazy(Supplier<T> thunk) {
        return new Lazy<>(thunk);
    }

    /**
     * Get the value, possibly initializing it.
     *
     * @return The value.
     */
    @Override
    public T get() {
        if (thunk != null) {
            value = thunk.get();
            thunk = null;
        }
        return value;
    }

    /**
     * Apply the function to the value when it is next retrieved.
     *
     * @param op The function to apply.
     */
    public void mapInPlace(UnaryOperator<T> op) {
        if (thunk == null) {
            T value = this.value;
            this.value = null;
            thunk = () -> op.apply(value);
        } else {
            Supplier<T> thunk = this.thunk;
            this.thunk = () -> op.apply(thunk.get());
        }
    }

    /**
     * Imperatively set the value, dropping the thunk.
     * <p>
     * This can be used to clear memory the last time
     * the value is used.
     *
     * @param value The new value.
     */
    public void set(T value) {
        this.thunk = null;
        this.value = value;
    }
}

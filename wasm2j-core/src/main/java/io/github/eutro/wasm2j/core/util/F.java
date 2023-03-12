package io.github.eutro.wasm2j.core.util;

import io.github.eutro.wasm2j.core.ssa.Function;

/**
 * A simple unary function.
 * <p>
 * Equivalent to {@link java.util.function.Function}, but doesn't collide with
 * {@link Function}.
 *
 * @param <A> The argument type.
 * @param <B> The return type.
 */
@FunctionalInterface
public interface F<A, B> {
    /**
     * Apply the function.
     *
     * @param a The argument.
     * @return The result.
     */
    B apply(A a);

    /**
     * Compose this function with another.
     *
     * @param g   The function to apply after this.
     * @param <C> The result type.
     * @return The composed function.
     */
    default <C> F<A, C> andThen(F<B, C> g) {
        return a -> g.apply(apply(a));
    }
}

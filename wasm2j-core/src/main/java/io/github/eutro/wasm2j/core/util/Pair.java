package io.github.eutro.wasm2j.core.util;

import java.util.Objects;

/**
 * A simple two-tuple, with defined equality and hashing.
 *
 * @param <L> The left type.
 * @param <R> The right type.
 */
public final class Pair<L, R> {
    /**
     * The left value.
     */
    public final L left;
    /**
     * The right value.
     */
    public final R right;

    /**
     * Create a pair.
     *
     * @param left The left value.
     * @param right The right value.
     */
    private Pair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    /**
     * Create a pair.
     *
     * @param left The left value.
     * @param right The right value.
     * @return The pair.
     * @param <L> The left type.
     * @param <R> The right type.
     */
    public static <L, R> Pair<L, R> of(L left, R right) {
        return new Pair<>(left, right);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pair<?, ?> pair = (Pair<?, ?>) o;
        return Objects.equals(left, pair.left) && Objects.equals(right, pair.right);
    }

    @Override
    public int hashCode() {
        return Objects.hash(left, right);
    }
}

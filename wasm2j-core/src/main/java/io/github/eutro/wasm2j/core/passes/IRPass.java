package io.github.eutro.wasm2j.core.passes;

import io.github.eutro.wasm2j.core.passes.misc.ChainedPass;
import io.github.eutro.wasm2j.core.ssa.Function;
import io.github.eutro.wasm2j.core.ssa.Module;
import io.github.eutro.wasm2j.core.ssa.JClass;

/**
 * A pass to run on some part of the IR (e.g. {@link Module}, {@link JClass}, {@link Function}),
 * which may modify the IR, or convert it to a different form.
 * <p>
 * A pass may be <i>in-place</i>, in which case it must have the same
 * input and result types, and should return true for {@link #isInPlace()}.
 *
 * @param <A> The input type.
 * @param <B> The result type.
 * @see io.github.eutro.wasm2j.core.ssa
 */
public interface IRPass<A, B> {
    /**
     * Run the pass.
     *
     * @param a The IR to run it on.
     * @return The result.
     */
    B run(A a);

    /**
     * Get whether this pass is in-place.
     *
     * @return Whether this pass is in-place.
     */
    default boolean isInPlace() {
        return false;
    }

    /**
     * Compose this pass with another.
     *
     * @param next The pass to run after this.
     * @return The composed pass.
     * @param <C> The result type.
     */
    default <C> IRPass<A, C> then(IRPass<B, C> next) {
        return new ChainedPass<>(this, next);
    }
}

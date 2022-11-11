package io.github.eutro.wasm2j.passes;

import io.github.eutro.wasm2j.passes.misc.ChainedPass;

public interface IRPass<A, B> {
    B run(A a);

    default boolean isInPlace() {
        return false;
    }

    default <C> IRPass<A, C> then(IRPass<B, C> next) {
        return new ChainedPass<>(this, next);
    }
}

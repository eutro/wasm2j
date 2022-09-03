package io.github.eutro.wasm2j.passes;

public interface IRPass<A, B> {
    B run(A a);

    default boolean isInPlace() {
        return false;
    }

    default <C> IRPass<A, C> then(IRPass<B, C> next) {
        return a -> next.run(run(a));
    }
}

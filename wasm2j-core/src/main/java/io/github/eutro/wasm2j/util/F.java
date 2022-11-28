package io.github.eutro.wasm2j.util;

public interface F<A, B> {
    B apply(A a);

    default <C> F<A, C> andThen(F<B, C> g) {
        return a -> g.apply(apply(a));
    }
}

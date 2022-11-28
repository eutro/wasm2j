package io.github.eutro.wasm2j.conf.api;

public interface ConventionModifier<Convention, Node> {
    Convention modify(Convention convention, Node node, int index);

    static <C, N> ConventionModifier<C, N> identity() {
        return (c, n, index) -> c;
    }
}

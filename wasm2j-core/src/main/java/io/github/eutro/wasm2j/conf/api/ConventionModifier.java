package io.github.eutro.wasm2j.conf.api;

public interface ConventionModifier<Convention, Node> {
    Convention modify(Convention convention, Node node, int index);

    static <C, N> ConventionModifier<C, N> identity() {
        return (c, n, index) -> c;
    }

    default ConventionModifier<Convention, Node> andThen(ConventionModifier<Convention, Node> next) {
        if (this == identity()) {
            return next;
        }
        return (convention, node, index) -> next.modify(modify(convention, node, index), node, index);
    }
}

package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.conf.api.WirJavaConventionFactory.Builder;

/**
 * A function which modifies a local convention.
 *
 * @param <Convention> The type of the convention.
 * @param <Node>       The type of the local {@link io.github.eutro.jwasm.tree} node.
 * @see Builder#setModifyFuncConvention(ConventionModifier)
 * @see Builder#setModifyGlobalConvention(ConventionModifier)
 * @see Builder#setModifyMemConvention(ConventionModifier)
 * @see Builder#setModifyTableConvention(ConventionModifier)
 */
public interface ConventionModifier<Convention, Node> {
    /**
     * Modify the given convention, possibly returning a new one that modifies its behaviour,
     * likely its export behaviour.
     *
     * @param convention The convention to modify.
     * @param node       The local node of the WebAssembly module.
     * @param index      The index of the node in its respective index space.
     * @return The modified convention.
     */
    Convention modify(Convention convention, Node node, int index);

    /**
     * Returns the identity convention modifier, which does nothing.
     *
     * @return The identity convention modifier.
     * @param <C> The convention type.
     * @param <N> The node type.
     */
    static <C, N> ConventionModifier<C, N> identity() {
        return (c, n, index) -> c;
    }

    /**
     * Compose this convention modifier with another.
     *
     * @param next The modifier to run after this.
     * @return The composed convention modifier.
     */
    default ConventionModifier<Convention, Node> andThen(ConventionModifier<Convention, Node> next) {
        if (this == identity()) {
            return next;
        }
        return (convention, node, index) -> next.modify(modify(convention, node, index), node, index);
    }
}

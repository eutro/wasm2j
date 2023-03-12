package io.github.eutro.wasm2j.api.bits;

/**
 * An extension that can be attached onto something.
 *
 * @param <Onto> What this can be attached onto.
 * @param <Ret>  What is returned on attaching.
 */
public interface Bit<Onto, Ret> {
    /**
     * Attach this to {@code cc}.
     *
     * @param cc The thing to attach this to.
     * @return The result.
     */
    Ret addTo(Onto cc);
}

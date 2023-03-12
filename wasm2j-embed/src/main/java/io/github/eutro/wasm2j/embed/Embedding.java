package io.github.eutro.wasm2j.embed;

/**
 * Declares that a method corresponds to a function defined in the
 * <a href="https://webassembly.github.io/spec/core/appendix/embedding.html">embedding</a>
 * section of the WebAssembly specification.
 * <p>
 * These are the stable entry points for plain runtime manipulation of WebAssembly,
 * without Java-specific extensions.
 */
public @interface Embedding {
    /**
     * Returns the name of the function implemented.
     *
     * @return The name of the function implemented.
     */
    String value();
}

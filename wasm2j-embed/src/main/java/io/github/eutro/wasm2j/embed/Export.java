package io.github.eutro.wasm2j.embed;

import io.github.eutro.wasm2j.support.ExternType;

/**
 * An export from a module, which is a name and its type.
 */
public class Export {
    /**
     * The name of the export.
     */
    public final String name;
    /**
     * The type of the export.
     */
    public final ExternType type;

    /**
     * Construct an export with the given name and type.
     *
     * @param name The name.
     * @param type The type.
     */
    public Export(String name, ExternType type) {
        this.name = name;
        this.type = type;
    }
}

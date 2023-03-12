package io.github.eutro.wasm2j.embed;

import io.github.eutro.wasm2j.api.types.ExternType;

/**
 * An import of a module.
 */
public class Import {
    /**
     * The name of the module to import from.
     */
    public final String module;
    /**
     * The name of the import.
     */
    public final String name;
    /**
     * The type of the import.
     */
    public final ExternType type;

    /**
     * Construct a new import with the given module name, name, and type.
     *
     * @param module The module name.
     * @param name   The import name.
     * @param type   The type.
     */
    public Import(String module, String name, ExternType type) {
        this.module = module;
        this.name = name;
        this.type = type;
    }

    @Override
    public String toString() {
        return type + " '" + name + "' from '" + module + '"';
    }
}

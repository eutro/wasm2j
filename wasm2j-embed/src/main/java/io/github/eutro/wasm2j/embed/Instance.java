package io.github.eutro.wasm2j.embed;

/**
 * An instance of a module, from which exports can be retrieved.
 */
public interface Instance {
    /**
     * Get a named export of the module.
     *
     * @param name The name of the export.
     * @return The export.
     */
    @Embedding("instance_export")
    ExternVal getExport(String name);
}

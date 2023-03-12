package io.github.eutro.wasm2j.embed;

import org.jetbrains.annotations.Nullable;

/**
 * An instance of a module, from which exports can be retrieved.
 */
public interface Instance {
    /**
     * Get a named export of the module instance.
     *
     * @param name The name of the export.
     * @return The export.
     */
    @Embedding("instance_export")
    @Nullable
    ExternVal getExport(String name);
}

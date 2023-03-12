package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.jwasm.tree.ExportNode;
import io.github.eutro.wasm2j.ssa.JClass;
import io.github.eutro.wasm2j.ssa.Module;

/**
 * A convention which may be exported from a module.
 *
 * @see WirJavaConvention
 */
public interface ExportableConvention {
    /**
     * Do whatever this convention defines as "exporting" the object from the module.
     *
     * @param node The export node.
     * @param module The WebAssembly module being compiled.
     * @param jClass The Java class being compiled into.
     */
    void export(ExportNode node, Module module, JClass jClass);

    /**
     * Returns an {@link ExportableConvention} that does nothing.
     *
     * @return The convention.
     */
    static ExportableConvention noop() {
        return (node, module, jClass) -> {};
    }
}

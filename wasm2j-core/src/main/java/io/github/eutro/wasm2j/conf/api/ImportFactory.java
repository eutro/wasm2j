package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.jwasm.tree.AbstractImportNode;
import io.github.eutro.wasm2j.ssa.JClass;
import io.github.eutro.wasm2j.ssa.Module;

import static io.github.eutro.wasm2j.conf.api.WirJavaConventionFactory.*;

/**
 * An interface for finding an import of a module.
 *
 * @param <Import>     The type of the import node.
 * @param <Convention> The convention type of things being imported.
 * @see Builder#setFunctionImports(ImportFactory)
 * @see Builder#setGlobalImports(ImportFactory)
 * @see Builder#setMemoryImports(ImportFactory)
 * @see Builder#setTableImports(ImportFactory)
 */
@FunctionalInterface
public interface ImportFactory<Import extends AbstractImportNode, Convention> {
    /**
     * Resolve an implementation of the given import, or throw an exception
     * if it does not exist.
     *
     * @param module     The module to look up the import in.
     * @param importNode The import node.
     * @param jClass     The class being compiled into.
     * @param idx        The index of the function, global, memory or table in the respective index
     *                   space of the module.
     * @return The convention of the resolved import.
     */
    Convention createImport(Module module, Import importNode, JClass jClass, int idx);
}

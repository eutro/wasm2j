package io.github.eutro.wasm2j.core.conf.api;

import io.github.eutro.jwasm.tree.AbstractImportNode;
import io.github.eutro.wasm2j.core.ssa.Module;
import io.github.eutro.wasm2j.core.ssa.JClass;

/**
 * An interface for finding an import of a module.
 *
 * @param <Import>     The type of the import node.
 * @param <Convention> The convention type of things being imported.
 * @see WirJavaConventionFactory.Builder#setFunctionImports(ImportFactory)
 * @see WirJavaConventionFactory.Builder#setGlobalImports(ImportFactory)
 * @see WirJavaConventionFactory.Builder#setMemoryImports(ImportFactory)
 * @see WirJavaConventionFactory.Builder#setTableImports(ImportFactory)
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

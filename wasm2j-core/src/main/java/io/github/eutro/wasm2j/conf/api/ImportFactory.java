package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.jwasm.tree.AbstractImportNode;
import io.github.eutro.wasm2j.ssa.JClass;
import io.github.eutro.wasm2j.ssa.Module;

public interface ImportFactory<Import extends AbstractImportNode, Convention> {
    Convention createImport(Module module, Import importNode, JClass jClass, int idx);
}

package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.jwasm.tree.AbstractImportNode;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ssa.Module;

public interface ImportFactory<Import extends AbstractImportNode, Convention> {
    Convention createImport(Module module, Import importNode, JavaExts.JavaClass jClass, int idx);
}

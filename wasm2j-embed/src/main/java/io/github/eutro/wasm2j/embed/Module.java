package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.jwasm.tree.analysis.ModuleValidator;

public class Module {
    private final ModuleNode node;
    private boolean validated = false;

    Module(ModuleNode node) {
        this.node = node;
    }

    ModuleNode getNode() {
        return node;
    }

    void validate() {
        if (!validated) {
            node.accept(new ModuleValidator());
            validated = true;
        }
    }
}

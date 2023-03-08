package io.github.eutro.wasm2j.events;

import io.github.eutro.jwasm.tree.ModuleNode;
import org.jetbrains.annotations.NotNull;

public class ReceiveModuleNodeEvent implements ModuleCompileEvent {
    @NotNull
    public ModuleNode module;

    public ReceiveModuleNodeEvent(@NotNull ModuleNode module) {
        this.module = module;
    }
}

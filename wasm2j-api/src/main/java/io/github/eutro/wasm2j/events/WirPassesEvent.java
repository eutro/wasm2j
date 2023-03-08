package io.github.eutro.wasm2j.events;

import io.github.eutro.wasm2j.ssa.Module;
import org.jetbrains.annotations.NotNull;

public class WirPassesEvent implements ModuleCompileEvent {
    @NotNull
    public Module wir;

    public WirPassesEvent(@NotNull Module wir) {
        this.wir = wir;
    }
}

package io.github.eutro.wasm2j.api.events;

import io.github.eutro.wasm2j.core.ssa.JClass;
import org.jetbrains.annotations.NotNull;

public class JirPassesEvent implements ModuleCompileEvent {
    @NotNull
    public JClass jir;

    public JirPassesEvent(@NotNull JClass jir) {
        this.jir = jir;
    }
}

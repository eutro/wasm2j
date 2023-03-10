package io.github.eutro.wasm2j.events;

import io.github.eutro.wasm2j.ssa.JClass;
import org.jetbrains.annotations.NotNull;

public class JirPassesEvent implements ModuleCompileEvent {
    @NotNull
    public JClass jir;

    public JirPassesEvent(@NotNull JClass jir) {
        this.jir = jir;
    }
}

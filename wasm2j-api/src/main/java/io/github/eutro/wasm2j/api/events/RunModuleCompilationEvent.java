package io.github.eutro.wasm2j.api.events;

import io.github.eutro.wasm2j.api.ModuleCompilation;
import org.jetbrains.annotations.NotNull;

public class RunModuleCompilationEvent implements CompilerEvent {
    @NotNull
    public ModuleCompilation compilation;

    public RunModuleCompilationEvent(@NotNull ModuleCompilation compilation) {
        this.compilation = compilation;
    }
}

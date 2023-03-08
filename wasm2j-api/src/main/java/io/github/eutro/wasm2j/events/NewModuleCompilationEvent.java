package io.github.eutro.wasm2j.events;

import io.github.eutro.wasm2j.ModuleCompilation;
import org.jetbrains.annotations.NotNull;

public class NewModuleCompilationEvent implements CompilerEvent {
    @NotNull
    public ModuleCompilation compilation;

    public NewModuleCompilationEvent(@NotNull ModuleCompilation compilation) {
        this.compilation = compilation;
    }
}

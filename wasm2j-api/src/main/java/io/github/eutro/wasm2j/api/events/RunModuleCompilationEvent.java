package io.github.eutro.wasm2j.api.events;

import io.github.eutro.wasm2j.api.ModuleCompilation;
import io.github.eutro.wasm2j.api.WasmCompiler;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a module compilation is started.
 *
 * @see WasmCompiler
 * @see ModuleCompilation
 */
public class RunModuleCompilationEvent implements CompilerEvent {
    /**
     * The module compilation.
     */
    @NotNull
    public ModuleCompilation compilation;

    /**
     * Construct a new module compilation event.
     *
     * @param compilation The compilation.
     */
    public RunModuleCompilationEvent(@NotNull ModuleCompilation compilation) {
        this.compilation = compilation;
    }
}

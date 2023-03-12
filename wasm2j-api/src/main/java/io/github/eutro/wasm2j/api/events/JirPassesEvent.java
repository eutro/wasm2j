package io.github.eutro.wasm2j.api.events;

import io.github.eutro.wasm2j.api.ModuleCompilation;
import io.github.eutro.wasm2j.core.ssa.JClass;
import org.jetbrains.annotations.NotNull;

/**
 * Fired immediately after the conversion from WebAssembly IR to
 * Java IR. The IR will be in SSA form.
 *
 * @see ModuleCompilation
 */
public class JirPassesEvent implements ModuleCompileEvent {
    /**
     * The Java IR.
     */
    @NotNull
    public JClass jir;

    /**
     * Construct a new Java IR passes event with the given IR.
     *
     * @param jir The IR.
     */
    public JirPassesEvent(@NotNull JClass jir) {
        this.jir = jir;
    }
}

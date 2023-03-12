package io.github.eutro.wasm2j.api.events;

import io.github.eutro.wasm2j.api.ModuleCompilation;
import io.github.eutro.wasm2j.core.ssa.Module;
import org.jetbrains.annotations.NotNull;

/**
 * An event fired just after the WebAssembly module is compiled to IR for the first time.
 * <p>
 * The IR is not yet in SSA form. If it is converted to SSA form during this event,
 * provided the metadata is not also invalidated, the work will not be re-done.
 *
 * @see ModuleCompilation
 */
public class WirPassesEvent implements ModuleCompileEvent {
    /**
     * The WebAssembly IR.
     * <p>
     * Likely not in SSA form.
     */
    @NotNull
    public Module wir;

    /**
     * Construct a new WirPassesEvent over the given IR.
     *
     * @param wir The WebAssembly IR.
     */
    public WirPassesEvent(@NotNull Module wir) {
        this.wir = wir;
    }
}

package io.github.eutro.wasm2j.events;

import io.github.eutro.wasm2j.ssa.Module;

public class JavaPreemitEvent implements ModuleCompileEvent {
    public Module jir;

    public JavaPreemitEvent(Module jir) {
        this.jir = jir;
    }
}

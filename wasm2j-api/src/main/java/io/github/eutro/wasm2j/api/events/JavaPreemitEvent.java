package io.github.eutro.wasm2j.api.events;

import io.github.eutro.wasm2j.core.ssa.JClass;

public class JavaPreemitEvent implements ModuleCompileEvent {
    public JClass jir;

    public JavaPreemitEvent(JClass jir) {
        this.jir = jir;
    }
}

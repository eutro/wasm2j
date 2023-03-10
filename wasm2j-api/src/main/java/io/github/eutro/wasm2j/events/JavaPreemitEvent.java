package io.github.eutro.wasm2j.events;

import io.github.eutro.wasm2j.ssa.JClass;

public class JavaPreemitEvent implements ModuleCompileEvent {
    public JClass jir;

    public JavaPreemitEvent(JClass jir) {
        this.jir = jir;
    }
}

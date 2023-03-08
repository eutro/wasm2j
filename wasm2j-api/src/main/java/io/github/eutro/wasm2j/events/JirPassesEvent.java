package io.github.eutro.wasm2j.events;

import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ssa.Module;
import org.jetbrains.annotations.NotNull;

public class JirPassesEvent implements ModuleCompileEvent {
    @NotNull
    public Module jir;

    public JirPassesEvent(@NotNull Module jir) {
        this.jir = jir;
    }

    public JavaExts.JavaClass getJClass() {
        return jir.getExtOrThrow(JavaExts.JAVA_CLASS);
    }
}

package io.github.eutro.wasm2j.events;

import io.github.eutro.wasm2j.conf.api.WirJavaConventionFactory;
import org.jetbrains.annotations.NotNull;

public class ModifyConventionsEvent implements ModuleCompileEvent {
    @NotNull
    public WirJavaConventionFactory.Builder conventionBuilder;

    public ModifyConventionsEvent(@NotNull WirJavaConventionFactory.Builder conventionBuilder) {
        this.conventionBuilder = conventionBuilder;
    }
}

package io.github.eutro.wasm2j.api.events;

import io.github.eutro.wasm2j.core.conf.api.WirJavaConventionFactory;
import org.jetbrains.annotations.NotNull;

public class ModifyConventionsEvent implements ModuleCompileEvent {
    @NotNull
    public WirJavaConventionFactory.Builder conventionBuilder;

    public ModifyConventionsEvent(@NotNull WirJavaConventionFactory.Builder conventionBuilder) {
        this.conventionBuilder = conventionBuilder;
    }
}

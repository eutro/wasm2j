package io.github.eutro.wasm2j.api.events;

import io.github.eutro.wasm2j.api.ModuleCompilation;
import io.github.eutro.wasm2j.core.conf.itf.WirJavaConventionFactory;
import org.jetbrains.annotations.NotNull;

/**
 * An event fired when constructing the {@link WirJavaConventionFactory Java conventions} of a
 * module compilation.
 *
 * @see ModuleCompilation
 * @see WirJavaConventionFactory.Builder
 */
public class ModifyConventionsEvent implements ModuleCompileEvent {
    /**
     * The convention builder.
     */
    @NotNull
    public WirJavaConventionFactory.Builder conventionBuilder;

    /**
     * Construct a new modify-conventions event with the given convention builder.
     *
     * @param conventionBuilder The builder.
     */
    public ModifyConventionsEvent(@NotNull WirJavaConventionFactory.Builder conventionBuilder) {
        this.conventionBuilder = conventionBuilder;
    }
}

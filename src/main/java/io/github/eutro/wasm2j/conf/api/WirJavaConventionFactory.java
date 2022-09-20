package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.ssa.Module;

public interface WirJavaConventionFactory {
    WirJavaConvention create(Module module);
}

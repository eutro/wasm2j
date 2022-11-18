package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.ssa.Module;

public interface WirJavaConventionFactory {
    WirJavaConvention create(Module module, Module extrasModule, IRPass<Function, Function> convertWir);
}

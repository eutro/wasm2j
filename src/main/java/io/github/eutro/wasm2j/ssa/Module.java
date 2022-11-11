package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.ExtHolder;

import java.util.LinkedHashSet;
import java.util.Set;

public final class Module extends ExtHolder {
    public Set<Function> functions = new LinkedHashSet<>();
}

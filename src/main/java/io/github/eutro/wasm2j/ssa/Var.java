package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.ExtHolder;

public final class Var extends ExtHolder {
    public String name;

    public Var(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return '$' + name;
    }
}

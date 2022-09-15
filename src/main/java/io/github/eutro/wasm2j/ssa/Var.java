package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.ExtHolder;

public final class Var extends ExtHolder {
    public String name;
    public int index;

    public Var(String name, int index) {
        this.name = name;
        this.index = index;
    }

    @Override
    public String toString() {
        return '$' + name + (index == 0 ? "" : "." + index);
    }
}

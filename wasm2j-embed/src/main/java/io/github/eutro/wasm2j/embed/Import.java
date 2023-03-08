package io.github.eutro.wasm2j.embed;

import io.github.eutro.wasm2j.support.ExternType;

public class Import {
    public final String module, name;
    public final ExternType type;

    public Import(String module, String name, ExternType type) {
        this.module = module;
        this.name = name;
        this.type = type;
    }

    @Override
    public String toString() {
        return type + " '" + name + "' from '" + module + '"';
    }
}

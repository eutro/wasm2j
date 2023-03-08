package io.github.eutro.wasm2j.embed;

import io.github.eutro.wasm2j.support.ExternType;

public class Export {
    public final String name;
    public final ExternType type;

    public Export(String name, ExternType type) {
        this.name = name;
        this.type = type;
    }
}

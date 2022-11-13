package io.github.eutro.wasm2j.embed;

public class ModuleExport {
    public final String name;
    public final ExternType type;

    public ModuleExport(String name, ExternType type) {
        this.name = name;
        this.type = type;
    }
}

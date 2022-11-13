package io.github.eutro.wasm2j.embed;

public class ModuleImport {
    public final String module, name;
    public final ExternType type;

    public ModuleImport(String module, String name, ExternType type) {
        this.module = module;
        this.name = name;
        this.type = type;
    }

    @Override
    public String toString() {
        return type + " '" + name + "' from '" + module + '"';
    }
}

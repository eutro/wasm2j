package io.github.eutro.wasm2j.embed;

public interface ModuleInst {
    ExternVal getExport(String name);
}

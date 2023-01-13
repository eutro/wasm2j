package io.github.eutro.wasm2j.embed;

public interface Instance {
    ExternVal getExport(String name);
}

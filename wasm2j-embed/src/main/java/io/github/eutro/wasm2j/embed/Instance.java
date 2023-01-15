package io.github.eutro.wasm2j.embed;

public interface Instance {
    @Embedding("instance_export")
    ExternVal getExport(String name);
}

package io.github.eutro.wasm2j.ext;

import java.util.Optional;

public interface ExtContainer {
    <T> void attachExt(Ext<T> ext, T value);

    <T> void removeExt(Ext<T> ext);

    <T> Optional<T> getExt(Ext<T> ext);

    default <T> T getExtOrThrow(Ext<T> ext) {
        return getExt(ext).orElseThrow(RuntimeException::new);
    }
}

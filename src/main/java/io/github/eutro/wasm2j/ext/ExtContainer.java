package io.github.eutro.wasm2j.ext;

import io.github.eutro.wasm2j.passes.IRPass;

import java.util.Optional;

public interface ExtContainer {
    <T> void attachExt(Ext<T> ext, T value);

    <T> void removeExt(Ext<T> ext);

    <T> Optional<T> getExt(Ext<T> ext);

    default <T> T getExtOrThrow(Ext<T> ext) {
        return getExt(ext).orElseThrow(RuntimeException::new);
    }

    default <T, O> T getExtOrRun(Ext<T> ext, O o, IRPass<O, ?> pass) {
        Optional<T> extV = getExt(ext);
        if (extV.isPresent()) return extV.get();
        pass.run(o);
        return getExtOrThrow(ext);
    }
}

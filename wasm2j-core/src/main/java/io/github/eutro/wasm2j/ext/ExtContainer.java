package io.github.eutro.wasm2j.ext;

import io.github.eutro.wasm2j.passes.IRPass;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public interface ExtContainer {
    <T> void attachExt(Ext<T> ext, T value);

    <T> void removeExt(Ext<T> ext);

    <T> @Nullable T getNullable(Ext<T> ext);

    default <T> Optional<T> getExt(Ext<T> ext) {
        return Optional.ofNullable(getNullable(ext));
    }

    default <T> T getExtOrThrow(Ext<T> ext) {
        T nullable = getNullable(ext);
        if (nullable != null) return nullable;
        throw new RuntimeException("Ext not present");
    }

    default <T, O> T getExtOrRun(Ext<T> ext, O o, IRPass<O, ?> pass) {
        T extV = getNullable(ext);
        if (extV != null) return extV;
        pass.run(o);
        return getExtOrThrow(ext);
    }
}

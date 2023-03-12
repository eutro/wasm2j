package io.github.eutro.wasm2j.ssa;

import io.github.eutro.jwasm.tree.ExprNode;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ext.ExtHolder;
import io.github.eutro.wasm2j.ext.WasmExts;
import io.github.eutro.wasm2j.util.Lazy;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents the IR of a WebAssembly module.
 */
public final class Module extends ExtHolder {
    /**
     * A map from expression nodes to functions.
     */
    public final Map<ExprNode, Lazy<Function>> funcMap = new LinkedHashMap<>();

    @Override
    public <T> void attachExt(Ext<T> ext, T value) {
        if (ext == WasmExts.FUNC_MAP) {
            throw new UnsupportedOperationException();
        }
        super.attachExt(ext, value);
    }

    @Override
    public <T> void removeExt(Ext<T> ext) {
        if (ext == WasmExts.FUNC_MAP) {
            throw new UnsupportedOperationException();
        }
        super.removeExt(ext);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> @Nullable T getNullable(Ext<T> ext) {
        if (ext == WasmExts.FUNC_MAP) {
            return (T) funcMap;
        }
        return super.getNullable(ext);
    }
}

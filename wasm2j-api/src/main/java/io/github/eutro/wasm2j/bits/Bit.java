package io.github.eutro.wasm2j.bits;

import io.github.eutro.wasm2j.WasmCompiler;

public interface Bit<Ret> {
    Ret addTo(WasmCompiler cc);
}

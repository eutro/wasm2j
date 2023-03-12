package io.github.eutro.wasm2j.api.bits;

public interface Bit<Onto, Ret> {
    Ret addTo(Onto cc);
}

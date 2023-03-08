package io.github.eutro.wasm2j.bits;

public interface Bit<Onto, Ret> {
    Ret addTo(Onto cc);
}

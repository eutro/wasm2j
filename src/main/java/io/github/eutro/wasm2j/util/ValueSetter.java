package io.github.eutro.wasm2j.util;

import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Var;

public interface ValueSetter {
    void set(IRBuilder ib, Var val);
}

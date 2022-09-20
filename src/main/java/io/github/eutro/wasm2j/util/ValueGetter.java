package io.github.eutro.wasm2j.util;

import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Var;

public interface ValueGetter {
    Var get(IRBuilder ib);
}

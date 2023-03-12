package io.github.eutro.wasm2j.core.util;

import io.github.eutro.wasm2j.core.ssa.IRBuilder;
import io.github.eutro.wasm2j.core.ssa.Var;

/**
 * A value that can be set.
 */
public interface ValueSetter {
    /**
     * Emit code to set this value.
     *
     * @param ib The instruction builder.
     * @param val The new value.
     */
    void set(IRBuilder ib, Var val);
}

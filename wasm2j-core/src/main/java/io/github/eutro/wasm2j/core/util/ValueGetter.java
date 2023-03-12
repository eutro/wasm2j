package io.github.eutro.wasm2j.core.util;

import io.github.eutro.wasm2j.core.ssa.IRBuilder;
import io.github.eutro.wasm2j.core.ssa.Var;

/**
 * A value that can be retrieved.
 */
public interface ValueGetter {
    /**
     * Emit code to retrieve the value.
     *
     * @param ib The instruction builder.
     * @return The value.
     */
    Var get(IRBuilder ib);
}

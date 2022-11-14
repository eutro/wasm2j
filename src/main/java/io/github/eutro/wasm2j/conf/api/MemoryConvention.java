package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;

public interface MemoryConvention extends ExportableConvention, ConstructorCallback {
    void emitMemLoad(IRBuilder ib, Effect effect);

    void emitMemStore(IRBuilder ib, Effect effect);

    void emitMemSize(IRBuilder ib, Effect effect);

    void emitMemGrow(IRBuilder ib, Effect effect);
}

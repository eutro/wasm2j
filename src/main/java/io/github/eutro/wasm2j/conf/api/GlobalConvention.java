package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;

public interface GlobalConvention extends ExportableConvention, ConstructorCallback {
    void emitGlobalRef(IRBuilder ib, Effect effect);

    void emitGlobalStore(IRBuilder ib, Effect effect);
}

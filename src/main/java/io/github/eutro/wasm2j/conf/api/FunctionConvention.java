package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;

public interface FunctionConvention extends ExportableConvention {
    void emitCall(IRBuilder ib, Effect effect);

    void emitFuncRef(IRBuilder ib, Effect effect);
}

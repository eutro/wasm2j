package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;

public interface TableConvention extends ExportableConvention {
    void emitTableRef(IRBuilder ib, Effect effect);

    void emitTableStore(IRBuilder ib, Effect effect);
}

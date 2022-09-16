package io.github.eutro.wasm2j.conf;

import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;

public interface WirJavaConvention {
    default void emitCall(IRBuilder ib, Effect effect) {
        throw new UnsupportedOperationException();
    }

    default void emitCallIndirect(IRBuilder ib, Effect effect) {
        throw new UnsupportedOperationException();
    }

    default void emitFuncRef(IRBuilder ib, Effect effect) {
        throw new UnsupportedOperationException();
    }

    default void emitMemLoad(IRBuilder ib, Effect effect) {
        throw new UnsupportedOperationException();
    }

    default void emitMemStore(IRBuilder ib, Effect effect) {
        throw new UnsupportedOperationException();
    }

    default void emitMemSize(IRBuilder ib, Effect effect) {
        throw new UnsupportedOperationException();
    }

    default void emitMemGrow(IRBuilder ib, Effect effect) {
        throw new UnsupportedOperationException();
    }

    default void emitTableRef(IRBuilder ib, Effect effect) {
        throw new UnsupportedOperationException();
    }

    default void emitTableStore(IRBuilder ib, Effect effect) {
        throw new UnsupportedOperationException();
    }

    default void emitGlobalRef(IRBuilder ib, Effect effect) {
        throw new UnsupportedOperationException();
    }

    default void emitGlobalStore(IRBuilder ib, Effect effect) {
        throw new UnsupportedOperationException();
    }

    default void buildConstructor() {}
}

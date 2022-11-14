package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.conf.impl.DelegatingExporter;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;

public interface FunctionConvention extends ExportableConvention, ConstructorCallback {
    void emitCall(IRBuilder ib, Effect effect);

    void emitFuncRef(IRBuilder ib, Effect effect);

    class Delegating extends DelegatingExporter implements FunctionConvention {
        protected final FunctionConvention delegate;

        public Delegating(FunctionConvention delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        public void emitCall(IRBuilder ib, Effect effect) {
            delegate.emitCall(ib, effect);
        }

        @Override
        public void emitFuncRef(IRBuilder ib, Effect effect) {
            delegate.emitFuncRef(ib, effect);
        }
    }
}

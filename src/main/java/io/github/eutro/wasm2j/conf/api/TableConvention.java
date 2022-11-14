package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.conf.impl.DelegatingExporter;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;

public interface TableConvention extends ExportableConvention, ConstructorCallback {
    void emitTableRef(IRBuilder ib, Effect effect);

    void emitTableStore(IRBuilder ib, Effect effect);

    void emitTableSize(IRBuilder ib, Effect effect);

    default void emitTableGrow(IRBuilder ib, Effect effect) {
        ib.insert(CommonOps.CONST.create(-1).insn().copyFrom(effect));
    }

    class Delegating extends DelegatingExporter implements TableConvention {
        protected final TableConvention delegate;

        public Delegating(TableConvention delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        public void emitTableRef(IRBuilder ib, Effect effect) {
            delegate.emitTableRef(ib, effect);
        }

        @Override
        public void emitTableStore(IRBuilder ib, Effect effect) {
            delegate.emitTableStore(ib, effect);
        }

        @Override
        public void emitTableSize(IRBuilder ib, Effect effect) {
            delegate.emitTableSize(ib, effect);
        }

        @Override
        public void emitTableGrow(IRBuilder ib, Effect effect) {
            delegate.emitTableGrow(ib, effect);
        }
    }
}

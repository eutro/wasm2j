package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.conf.impl.DelegatingExporter;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Module;

public interface TableConvention extends ExportableConvention, ConstructorCallback {
    void emitTableRef(IRBuilder ib, Effect effect);

    void emitTableStore(IRBuilder ib, Effect effect);

    void emitTableSize(IRBuilder ib, Effect effect);

    default void emitTableGrow(IRBuilder ib, Effect effect) {
        ib.insert(CommonOps.constant(-1).copyFrom(effect));
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

        @Override
        public void modifyConstructor(IRBuilder ib, JavaExts.JavaMethod ctorMethod, Module module, JavaExts.JavaClass jClass) {
            delegate.modifyConstructor(ib, ctorMethod, module, jClass);
        }
    }
}

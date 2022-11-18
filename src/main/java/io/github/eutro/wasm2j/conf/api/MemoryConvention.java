package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.conf.impl.DelegatingExporter;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.Var;

public interface MemoryConvention extends ExportableConvention, ConstructorCallback {
    void emitMemLoad(IRBuilder ib, Effect effect);

    void emitMemStore(IRBuilder ib, Effect effect);

    void emitMemSize(IRBuilder ib, Effect effect);

    void emitMemGrow(IRBuilder ib, Effect effect);

    void emitMemInit(IRBuilder ib, Effect effect, Var data);

    class Delegating extends DelegatingExporter implements MemoryConvention {
        protected final MemoryConvention delegate;

        public Delegating(MemoryConvention delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        public void emitMemLoad(IRBuilder ib, Effect effect) {
            delegate.emitMemLoad(ib, effect);
        }

        @Override
        public void emitMemStore(IRBuilder ib, Effect effect) {
            delegate.emitMemStore(ib, effect);
        }

        @Override
        public void emitMemSize(IRBuilder ib, Effect effect) {
            delegate.emitMemSize(ib, effect);
        }

        @Override
        public void emitMemGrow(IRBuilder ib, Effect effect) {
            delegate.emitMemGrow(ib, effect);
        }

        @Override
        public void emitMemInit(IRBuilder ib, Effect effect, Var data) {
            delegate.emitMemInit(ib, effect, data);
        }

        @Override
        public void modifyConstructor(IRBuilder ib, JavaExts.JavaMethod ctorMethod, Module module, JavaExts.JavaClass jClass) {
            delegate.modifyConstructor(ib, ctorMethod, module, jClass);
        }
    }
}

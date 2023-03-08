package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.jwasm.tree.ExportNode;
import io.github.eutro.wasm2j.conf.impl.DelegatingExporter;
import io.github.eutro.wasm2j.ext.ExtContainer;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Module;

public interface GlobalConvention extends ExportableConvention, ConstructorCallback, ExtContainer {
    void emitGlobalRef(IRBuilder ib, Effect effect);

    void emitGlobalStore(IRBuilder ib, Effect effect);

    class Delegating extends DelegatingExporter implements GlobalConvention {
        protected final GlobalConvention delegate;

        public Delegating(GlobalConvention delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        public void emitGlobalRef(IRBuilder ib, Effect effect) {
            delegate.emitGlobalRef(ib, effect);
        }

        @Override
        public void emitGlobalStore(IRBuilder ib, Effect effect) {
            delegate.emitGlobalStore(ib, effect);
        }

        @Override
        public void export(ExportNode node, Module module, JavaExts.JavaClass jClass) {
            delegate.export(node, module, jClass);
        }

        @Override
        public void modifyConstructor(IRBuilder ib, JavaExts.JavaMethod ctorMethod, Module module, JavaExts.JavaClass jClass) {
            delegate.modifyConstructor(ib, ctorMethod, module, jClass);
        }
    }
}

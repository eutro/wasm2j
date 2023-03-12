package io.github.eutro.wasm2j.core.conf.itf;

import io.github.eutro.jwasm.tree.ExportNode;
import io.github.eutro.wasm2j.core.conf.impl.DelegatingExporter;
import io.github.eutro.wasm2j.core.ext.ExtContainer;
import io.github.eutro.wasm2j.core.ssa.Effect;
import io.github.eutro.wasm2j.core.ssa.IRBuilder;
import io.github.eutro.wasm2j.core.ssa.Module;
import io.github.eutro.wasm2j.core.ops.WasmOps;
import io.github.eutro.wasm2j.core.ssa.JClass;

/**
 * The convention for a WebAssembly global, defining how the function can be referenced, set, exported, and
 * added to the constructor.
 *
 * @see WirJavaConvention
 */
public interface GlobalConvention extends ExportableConvention, ConstructorCallback, ExtContainer {
    /**
     * Emit code for a WebAssembly {@code global.get} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#GLOBAL_REF},<br>
     * with no arguments;<br>
     * it will assign to one variable: the retrieved value of the global.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     */
    void emitGlobalRef(IRBuilder ib, Effect effect);

    /**
     * Emit code for a WebAssembly {@code global.set} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#GLOBAL_SET},<br>
     * with one argument: the value to store in the global;<br>
     * it will assign to no variables.
     * <p>
     * If the global is immutable, this method is free to throw an exception,
     * or do anything else it deems appropriate.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     */
    void emitGlobalStore(IRBuilder ib, Effect effect);

    /**
     * A {@link GlobalConvention} which delegates all calls to another.
     */
    class Delegating extends DelegatingExporter implements GlobalConvention {
        /**
         * The convention calls will be delegated to.
         */
        protected final GlobalConvention delegate;

        /**
         * Construct a new {@link Delegating} with the given delegate.
         *
         * @param delegate The delegate.
         */
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
        public void export(ExportNode node, Module module, JClass jClass) {
            delegate.export(node, module, jClass);
        }

        @Override
        public void modifyConstructor(IRBuilder ib, JClass.JavaMethod ctorMethod, Module module, JClass jClass) {
            delegate.modifyConstructor(ib, ctorMethod, module, jClass);
        }
    }
}

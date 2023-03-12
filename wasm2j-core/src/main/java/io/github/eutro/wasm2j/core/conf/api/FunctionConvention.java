package io.github.eutro.wasm2j.core.conf.api;

import io.github.eutro.wasm2j.core.conf.impl.DelegatingExporter;
import io.github.eutro.wasm2j.core.ext.ExtContainer;
import io.github.eutro.wasm2j.core.ssa.Effect;
import io.github.eutro.wasm2j.core.ssa.IRBuilder;
import io.github.eutro.wasm2j.core.ssa.Module;
import io.github.eutro.wasm2j.core.ops.WasmOps;
import io.github.eutro.wasm2j.core.ssa.JClass;

/**
 * The convention for a WebAssembly function, defining how the function can be called, referenced, exported, and
 * added to the constructor.
 *
 * @see WirJavaConvention
 */
public interface FunctionConvention extends ExportableConvention, ConstructorCallback, ExtContainer {
    /**
     * Emit code for a WebAssembly {@code call} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#CALL},<br>
     * with as many arguments as the function has parameter types;<br>
     * it will assign to as many variables as the function has return types.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     */
    void emitCall(IRBuilder ib, Effect effect);

    /**
     * Emit code for a WebAssembly {@code ref.func} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#FUNC_REF},<br>
     * with no arguments;<br>
     * it will assign to one variable: the returned function reference.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     */
    void emitFuncRef(IRBuilder ib, Effect effect);

    /**
     * A {@link FunctionConvention} which delegates all calls to another.
     */
    class Delegating extends DelegatingExporter implements FunctionConvention {
        /**
         * The convention calls will be delegated to.
         */
        protected final FunctionConvention delegate;

        /**
         * Construct a new {@link Delegating} with the given delegate.
         *
         * @param delegate The delegate.
         */
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

        @Override
        public void modifyConstructor(IRBuilder ib, JClass.JavaMethod ctorMethod, Module module, JClass jClass) {
            delegate.modifyConstructor(ib, ctorMethod, module, jClass);
        }
    }
}

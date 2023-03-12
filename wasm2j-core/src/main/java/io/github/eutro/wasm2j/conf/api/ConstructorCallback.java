package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.JClass;
import io.github.eutro.wasm2j.ssa.Module;

/**
 * A callback which is run by the default {@link WirJavaConvention} to modify the constructor in some way.
 */
public interface ConstructorCallback {
    /**
     * Modify the constructor in any way.
     *
     * @param ib         The instruction builder.
     * @param ctorMethod The Java method which is the constructor.
     * @param module     The WebAssembly module being compiled.
     * @param jClass     The Java class being compiled into.
     */
    default void modifyConstructor(IRBuilder ib, JClass.JavaMethod ctorMethod, Module module, JClass jClass) {
    }

    /**
     * A subclass of {@link ConstructorCallback} without the no-op default implementation for
     * {@link #modifyConstructor(IRBuilder, JClass.JavaMethod, Module, JClass)}, so
     * it can be the target of a lambda expression.
     */
    @FunctionalInterface
    interface Abstract extends ConstructorCallback {
        /**
         * {@inheritDoc}
         *
         * @param ib         The instruction builder.
         * @param ctorMethod The Java method which is the constructor.
         * @param module     The WebAssembly module being compiled.
         * @param jClass     The Java class being compiled into.
         */
        @Override
        void modifyConstructor(IRBuilder ib, JClass.JavaMethod ctorMethod, Module module, JClass jClass);
    }
}

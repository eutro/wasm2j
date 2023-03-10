package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.JClass;
import io.github.eutro.wasm2j.ssa.Module;

public interface ConstructorCallback {
    default void modifyConstructor(IRBuilder ib, JClass.JavaMethod ctorMethod, Module module, JClass jClass) {}

    @FunctionalInterface
    interface Abstract extends ConstructorCallback {
        @Override
        void modifyConstructor(IRBuilder ib, JClass.JavaMethod ctorMethod, Module module, JClass jClass);
    }
}

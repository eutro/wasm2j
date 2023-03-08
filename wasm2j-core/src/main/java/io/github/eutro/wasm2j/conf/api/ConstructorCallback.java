package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Module;

public interface ConstructorCallback {
    default void modifyConstructor(IRBuilder ib, JavaExts.JavaMethod ctorMethod, Module module, JavaExts.JavaClass jClass) {}

    @FunctionalInterface
    interface Abstract extends ConstructorCallback {
        @Override
        void modifyConstructor(IRBuilder ib, JavaExts.JavaMethod ctorMethod, Module module, JavaExts.JavaClass jClass);
    }
}

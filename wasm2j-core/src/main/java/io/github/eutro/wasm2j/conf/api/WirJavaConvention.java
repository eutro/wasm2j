package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.ssa.Function;

public interface WirJavaConvention {
    FunctionConvention getFunction(int index);

    GlobalConvention getGlobal(int index);

    MemoryConvention getMemory(int index);

    TableConvention getTable(int index);

    DataConvention getData(int index);

    ElemConvention getElem(int index);

    default CallingConvention getIndirectCallingConvention() {
        throw new UnsupportedOperationException();
    }

    default void convert(IRPass<Function, Function> convertPass) {}
}

package io.github.eutro.wasm2j.conf.api;

public interface WirJavaConvention {
    FunctionConvention getFunction(int index);

    GlobalConvention getGlobal(int index);

    MemoryConvention getMemory(int index);

    TableConvention getTable(int index);

    DataConvention getData(int data);

    default CallingConvention getIndirectCallingConvention() {
        throw new UnsupportedOperationException();
    }

    default void preConvert() {}

    default void postConvert() {}
}

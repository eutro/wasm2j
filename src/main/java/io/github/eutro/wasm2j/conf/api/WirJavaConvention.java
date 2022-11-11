package io.github.eutro.wasm2j.conf.api;

public interface WirJavaConvention {
    default FunctionConvention getFunction(int index) {
        throw new UnsupportedOperationException();
    }

    default GlobalConvention getGlobal(int index) {
        throw new UnsupportedOperationException();
    }

    default MemoryConvention getMemory(int index) {
        throw new UnsupportedOperationException();
    }

    default TableConvention getTable(int index) {
        throw new UnsupportedOperationException();
    }

    default CallingConvention getIndirectCallingConvention() {
        throw new UnsupportedOperationException();
    }

    default void preEmit() {}

    default void buildConstructor() {}
}

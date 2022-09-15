package io.github.eutro.wasm2j.passes;

public interface InPlaceIRPass<T> extends IRPass<T, T> {
    void runInPlace(T t);

    @Override
    default T run(T t) {
        runInPlace(t);
        return t;
    }

    @Override
    default boolean isInPlace() {
        return true;
    }
}

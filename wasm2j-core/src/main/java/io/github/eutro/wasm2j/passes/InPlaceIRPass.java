package io.github.eutro.wasm2j.passes;

/**
 * An IR pass which is {@link #isInPlace() in-place}.
 * <p>
 * Not all in-place IR passes are instances of this, however.
 *
 * @param <T> The type of the IR this pass operates on.
 */
public interface InPlaceIRPass<T> extends IRPass<T, T> {
    /**
     * Run the pass.
     *
     * @param t The IR to run this pass on.
     */
    void runInPlace(T t);

    @Override
    default T run(T t) {
        runInPlace(t);
        return t;
    }

    /**
     * {@inheritDoc}
     *
     * @return {@code true}
     */
    @Override
    default boolean isInPlace() {
        return true;
    }
}

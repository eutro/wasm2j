package io.github.eutro.wasm2j.ext;

import io.github.eutro.wasm2j.passes.IRPass;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * A container for {@link Ext}s. See the {@link io.github.eutro.wasm2j.ext package-level documentation} for more info.
 */
public interface ExtContainer {
    /**
     * Associate {@code ext} with {@code value} in this container.
     *
     * @param ext   The ext.
     * @param value The value the ext has in this container.
     * @param <T>   The type of the ext.
     */
    <T> void attachExt(Ext<T> ext, T value);

    /**
     * Disassociate the value of {@code ext} (if any) in this container.
     *
     * @param ext The ext.
     * @param <T> The type of the ext.
     */
    <T> void removeExt(Ext<T> ext);

    /**
     * Get the value associated with {@code ext} in this container, or null if not present.
     * <p>
     * If an ext was attached with {@link #attachExt(Ext, Object)}, it should be returned,
     * unless it was later removed with a call to {@link #removeExt(Ext)}.
     *
     * @param ext The ext.
     * @param <T> The type of the ext.
     * @return The value associated with ext in this container.
     */
    <T> @Nullable T getNullable(Ext<T> ext);

    /**
     * Get the value associated with {@code ext} in this container, if any.
     *
     * @param ext The ext.
     * @param <T> The type of the ext.
     * @return The value associated with the ext in this container.
     * @see #getNullable(Ext)
     */
    default <T> Optional<T> getExt(Ext<T> ext) {
        return Optional.ofNullable(getNullable(ext));
    }

    /**
     * Get the value associated with {@code ext} in this container, or throw an exception if not present.
     *
     * @param ext The ext.
     * @param <T> The type of the ext.
     * @return The value associated with the ext in this container.
     * @see #getNullable(Ext)
     */
    default <T> T getExtOrThrow(Ext<T> ext) {
        T nullable = getNullable(ext);
        if (nullable != null) return nullable;
        throw new RuntimeException("Ext not present");
    }

    /**
     * Get the value associated with {@code ext} in this container, or run the given pass and try again.
     *
     * @param ext The ext.
     * @param o The object to run the pass on.
     * @param pass The pass to run.
     * @param <T> The type of the ext.
     * @param <O> The type the pass operates on.
     * @return The value associated with the ext in this container.
     * @see #getNullable(Ext)
     */
    default <T, O> T getExtOrRun(Ext<T> ext, O o, IRPass<O, ?> pass) {
        T extV = getNullable(ext);
        if (extV != null) return extV;
        pass.run(o);
        return getExtOrThrow(ext);
    }
}

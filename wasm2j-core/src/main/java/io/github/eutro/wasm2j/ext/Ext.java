package io.github.eutro.wasm2j.ext;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An ext, that can be associated with a value (of type {@code T})
 * in an {@link ExtContainer}.
 * <p>
 * Note: The implementation of {@link #compareTo(Ext)} depends
 * on the global order in which exts are {@link #create(Class, String) create}d.
 * It may thus subtly change between different versions and program
 * executions.
 *
 * @param <T> The type of the ext.
 */
public class Ext<T> implements Comparable<Ext<?>> {
    private static final AtomicInteger ID_COUNTER = new AtomicInteger(0);

    private final Class<T> type;
    private final int id = ID_COUNTER.getAndIncrement();
    private final String name;

    private Ext(Class<T> type, String name) {
        this.type = type;
        this.name = name;
    }

    /**
     * Creates a new ext, with the subtype of a given class, and the given name.
     * <p>
     * The reason for the funny signature is that classes are not generic,
     * that is, the type variable {@code T} here cannot refer to a generic class.
     * The class given is unimportant for the ext API, but may aid with debugging.
     *
     * @param type The most specific superclass of the type of the ext.
     * @param name The name of the ext.
     * @param <T>  The type of the class.
     * @param <R>  The type of the ext.
     * @return The new ext.
     */
    @SuppressWarnings("unchecked")
    public static <T, R extends T> Ext<R> create(Class<T> type, String name) {
        return (Ext<R>) new Ext<>(type, name);
    }

    /**
     * Retrieve the type of the ext that it was {@link #create(Class, String) created} with.
     * <p>
     * The type here is a slight lie. {@code T} may be generic, but {@link Class}es themselves cannot
     * refer to a generic type. When using this to {@link Class#cast(Object)}, be aware of the
     * possible heap pollution.
     *
     * @return The type of this ext.
     */
    public Class<T> getType() {
        return type;
    }

    /**
     * Get the association of this in the given container.
     *
     * @param ec The container.
     * @return The association.
     * @see ExtContainer#getExt(Ext)
     */
    public Optional<T> getIn(ExtContainer ec) {
        return ec.getExt(this);
    }

    @Override
    public int compareTo(@NotNull Ext<?> o) {
        return Integer.compare(id, o.id);
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        return name + ": " + type.getName();
    }
}

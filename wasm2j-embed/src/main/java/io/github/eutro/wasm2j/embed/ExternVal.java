package io.github.eutro.wasm2j.embed;

import io.github.eutro.wasm2j.support.ExternType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * An extern, a value that can be exported from a module instance, or provided as an import.
 */
public interface ExternVal {
    /**
     * Get the type of this extern.
     *
     * @return The type.
     */
    @NotNull ExternType getType();

    /**
     * Check whether this extern matches the given expected type.
     *
     * @param expected The expected type, or null if only the kind should be matched.
     * @param kind     The expected kind.
     * @return Whether the type matched.
     */
    default boolean matchesType(@Nullable ExternType expected, ExternType.Kind kind) {
        ExternType type = getType();
        if (expected != null && !expected.assignableFrom(type)) {
            return false;
        }
        return type.getKind() == kind;
    }

    /**
     * Get this as a table, or throw an exception.
     *
     * @return The table.
     */
    @GeneratedAccess
    default Table getAsTable() {
        if (this instanceof Table) return (Table) this;
        throw new IllegalArgumentException("Not a table");
    }

    /**
     * Get this as a global, or throw an exception.
     *
     * @return The global.
     */
    @GeneratedAccess
    default Global getAsGlobal() {
        if (this instanceof Global) return (Global) this;
        throw new IllegalArgumentException("Not a global");
    }

    /**
     * Get this as a memory, or throw an exception.
     *
     * @return The memory.
     */
    @GeneratedAccess
    default Memory getAsMemory() {
        if (this instanceof Memory) return (Memory) this;
        throw new IllegalArgumentException("Not a memory");
    }

    /**
     * Get this as a function, or throw an exception.
     *
     * @return The function.
     */
    @GeneratedAccess
    default Func getAsFunc() {
        if (this instanceof Func) return (Func) this;
        throw new IllegalArgumentException("Not a func");
    }

    /**
     * Get the underlying method handle of this function.
     * <p>
     * Throws an exception if this is not a function.
     *
     * @return The method handle.
     */
    default MethodHandle getAsHandleRaw() {
        return getAsFunc().handle();
    }

    /**
     * Get this function as a method handle of the given type.
     * <p>
     * Throws an exception if this is not a function, or cannot be cast to the type.
     *
     * @param type The type of the handle.
     * @return The cast handle.
     */
    @GeneratedAccess
    default MethodHandle getAsHandle(MethodType type) {
        return getAsHandleRaw().asType(type);
    }

    /**
     * Create a function {@link ExternVal} directly from a {@link MethodHandle}.
     *
     * @param handle The method handle.
     * @return The {@link ExternVal}.
     */
    static ExternVal func(MethodHandle handle) {
        return Func.HandleFunc.create(null, handle);
    }

    /**
     * Create a function {@link ExternVal} from an instance of a functional interface.
     * <p>
     * The class provided must be suitable for an {@link FunctionalInterface} annotation.
     * That is, it must be an interface with exactly one abstract method.
     * <p>
     * This method is provided to make creating function {@link ExternVal}s easy from Java.
     *
     * <pre>{@code
     * // using existing functional interfaces
     * ExternVal.func(Runnable.class, () -> System.out.println("Hello, world!"));
     * ExternVal.func(IntUnaryOperator.class, x -> -x);
     * // using ad-hoc functional interfaces
     * interface IntIntDoubleDouble { double call(int x, int y, double z); }
     * ExternVal.func(IntIntDoubleDouble.class, (x, y, z) -> x + y * z);
     * }</pre>
     *
     * @param functionalInterfaceClass The functional interface class.
     * @param f                        An instance of the functional interface.
     * @param <F>                      The type of the function.
     * @return The {@link ExternVal}.
     */
    static <F> ExternVal func(Class<? super F> functionalInterfaceClass, F f) {
        Method[] methods = functionalInterfaceClass.getMethods();
        Method abstractMethod = null;
        for (Method method : methods) {
            if (Modifier.isAbstract(method.getModifiers())) {
                if (abstractMethod != null) {
                    abstractMethod = null;
                    break;
                }
                abstractMethod = method;
            }
        }
        if (abstractMethod == null || !Modifier.isInterface(functionalInterfaceClass.getModifiers())) {
            throw new IllegalArgumentException("Not a functional interface: " + functionalInterfaceClass.getName());
        }
        try {
            return func(MethodHandles.lookup().unreflect(abstractMethod).bindTo(f));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}

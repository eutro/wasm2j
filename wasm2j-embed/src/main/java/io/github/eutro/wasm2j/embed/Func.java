package io.github.eutro.wasm2j.embed;

import io.github.eutro.wasm2j.embed.internal.Utils;
import io.github.eutro.wasm2j.support.ExternType;
import io.github.eutro.wasm2j.support.ValType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;

public interface Func extends ExternVal {
    /**
     * Get the {@link MethodHandle} to the function.
     * <p>
     * The rules for converting a WebAssembly function {@code [p1*] -> [r1*]}
     * to a Java method signature {@code (p2*)r2} are as follows:
     * <ul>
     * <li>Every parameter {@code p1} of {@code p1*} is mapped directly to a parameter
     * {@code p2} in {@code p2*} according to the rules given in {@link ValType}.</li>
     * <li>If {@code r1*} is empty (the function has no results), {@code r2} is void.</li>
     * <li>If {@code r1*} is exactly one value, {@code r2} is
     * its conversion as given in {@link ValType}.</li>
     * <li>If {@code r1*} is more than 1 value, but homogenous (every {@code r1} is identical),
     * then {@code r2} is a Java array of the converted value according to {@link ValType}.</li>
     * <li>If {@code r1*} is more than 1 value, and heterogeneous (not all of the same type),
     * then {@code r2} is {@code Object[]}, containing every result boxed.</li>
     * </ul>
     *
     * @return The handle.
     */
    MethodHandle handle();

    @Embedding("func_type")
    @Override
    ExternType.@NotNull Func getType();

    /**
     * Invoke the function in a generic way.
     * <p>
     * When the type is known, directly invoking the {@link #handle() handle} should be preferred.
     *
     * @param args The arguments to the function.
     * @return The results of the function call.
     * @throws RuntimeException If the function traps.
     */
    @Embedding("func_invoke")
    default Object[] invoke(Object... args) {
        try {
            Object result = handle().invokeWithArguments(args);
            byte[] resultTypes = getType().results;
            if (resultTypes == null) {
                return new Object[]{result};
            }
            Object[] results = new Object[resultTypes.length];
            switch (results.length) {
                case 0:
                    break;
                case 1: {
                    results[0] = result;
                    break;
                }
                default: {
                    for (int i = 0; i < results.length; i++) {
                        results[i] = Array.get(result, i);
                    }
                }
            }
            return results;
        } catch (Throwable t) {
            throw Utils.rethrow(t);
        }
    }

    class HandleFunc implements Func {
        private final ExternType.Func type;
        private final MethodHandle handle;

        @Embedding("func_alloc")
        public HandleFunc(@Nullable ExternType.Func type, MethodHandle handle) {
            this.type = type == null ? new ExternType.Func(handle.type()) : type;
            this.handle = handle;
        }

        public static HandleFunc create(ExternType.Func type, MethodHandle handle) {
            return new HandleFunc(type, handle);
        }

        @Override
        public MethodHandle handle() {
            return handle;
        }

        @Override
        public ExternType.@NotNull Func getType() {
            return type;
        }
    }
}

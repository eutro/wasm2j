package io.github.eutro.wasm2j.embed;

import io.github.eutro.wasm2j.embed.internal.Utils;
import io.github.eutro.wasm2j.api.types.ExternType;
import io.github.eutro.wasm2j.api.types.ValType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * A global extern.
 */
public interface Global extends ExternVal {
    /**
     * Get the value of this global.
     *
     * @return The value of the global.
     */
    @Embedding("global_read")
    @GeneratedAccess
    Object get();

    /**
     * Set the value of this global, if it is mutable.
     *
     * @param value The value.
     */
    @Embedding("global_write")
    @GeneratedAccess
    void set(Object value);

    /**
     * Get the type of this global.
     *
     * @return The type.
     */
    @Embedding("global_type")
    @NotNull
    @Override
    ExternType.Global getType();

    /**
     * A global that stores its value directly.
     */
    class BoxGlobal implements Global {
        private final ValType type;
        private Object value;
        private boolean isMut = true;
        /**
         * A handle which casts the value to our type.
         */
        private final MethodHandle checkHandle;

        /**
         * Construct a box global with the given type and initial value.
         *
         * @param type  The value type.
         * @param value The initial value.
         */
        public BoxGlobal(ValType type, Object value) {
            this.type = type;
            checkHandle = MethodHandles
                    .identity(type.getType())
                    .asType(MethodType.methodType(Object.class, Object.class));
            this.value = checkValue(value);
        }

        /**
         * Construct a box global with the given type, initialised to null, or zero.
         *
         * @param type The value type.
         */
        @Embedding("global_alloc")
        public BoxGlobal(ExternType.Global type) {
            this(type.type, null);
            setMut(type.isMut);
        }

        /**
         * Set the mutability of this global.
         *
         * @param isMut The new mutability.
         * @return This, for convenience.
         */
        public BoxGlobal setMut(boolean isMut) {
            this.isMut = isMut;
            return this;
        }

        @Override
        public Object get() {
            return value;
        }

        @Override
        public void set(Object value) {
            if (!isMut) throw new UnsupportedOperationException();
            this.value = checkValue(value);
        }

        private Object checkValue(Object value) {
            try {
                return checkHandle.invokeExact(value);
            } catch (Throwable t) {
                throw Utils.rethrow(t);
            }
        }

        @NotNull
        @Override
        public ExternType.Global getType() {
            return new ExternType.Global(isMut, type);
        }
    }

    /**
     * A global that gets and sets through method handles.
     */
    class HandleGlobal implements Global {
        private final ValType type;
        private final MethodHandle get;
        private final @Nullable MethodHandle set;

        /**
         * Construct a global with the given value type, getter and setter.
         *
         * @param type The value type.
         * @param get The getter.
         * @param set The setter (or null if immutable).
         */
        public HandleGlobal(ValType type, MethodHandle get, @Nullable MethodHandle set) {
            this.type = type;
            this.get = get.asType(MethodType.methodType(Object.class));
            this.set = set == null ? null : set.asType(MethodType.methodType(void.class, Object.class));
        }

        /**
         * Calls {@link HandleGlobal#HandleGlobal(ValType, MethodHandle, MethodHandle)}.
         *
         * @param type The value type.
         * @param get The getter.
         * @param set The setter (or null if immutable).
         * @return The new global.
         */
        @GeneratedAccess
        public static HandleGlobal create(byte type, MethodHandle get, @Nullable MethodHandle set) {
            return new HandleGlobal(ValType.fromOpcode(type), get, set);
        }

        @Override
        public Object get() {
            try {
                return get.invokeExact();
            } catch (Throwable e) {
                throw Utils.rethrow(e);
            }
        }

        @Override
        public void set(Object value) {
            if (set != null) {
                try {
                    set.invokeExact(value);
                } catch (Throwable e) {
                    throw Utils.rethrow(e);
                }
            } else {
                throw new UnsupportedOperationException();
            }
        }

        @NotNull
        @Override
        public ExternType.Global getType() {
            return new ExternType.Global(set != null, type);
        }
    }
}

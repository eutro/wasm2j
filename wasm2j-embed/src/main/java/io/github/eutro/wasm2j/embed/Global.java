package io.github.eutro.wasm2j.embed;

import io.github.eutro.wasm2j.embed.internal.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

public interface Global extends ExternVal {
    @Embedding("global_read")
    @GeneratedAccess
    Object get();

    @Embedding("global_write")
    @GeneratedAccess
    void set(Object value);

    @Embedding("global_type")
    @Override
    ExternType.@NotNull Global getType();

    class BoxGlobal implements Global {
        private final ValType type;
        private Object value;
        private boolean isMut = true;
        private final MethodHandle checkHandle;

        public BoxGlobal(ValType type) {
            this(type, null);
        }

        public BoxGlobal(ValType type, Object value) {
            this.type = type;
            checkHandle = MethodHandles
                    .identity(type.getType())
                    .asType(MethodType.methodType(Object.class, Object.class));
            this.value = checkValue(value);
        }

        @Embedding("global_alloc")
        public BoxGlobal(ExternType.Global type) {
            this(type.type, null);
            setMut(type.isMut);
        }

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
            if (!isMut) throw new IllegalStateException();
            this.value = checkValue(value);
        }

        private Object checkValue(Object value) {
            try {
                return checkHandle.invokeExact(value);
            } catch (Throwable t) {
                throw Utils.rethrow(t);
            }
        }

        @Override
        public ExternType.@NotNull Global getType() {
            return new ExternType.Global(isMut, type);
        }
    }

    class HandleGlobal implements Global {
        private final ValType type;
        private final MethodHandle get;
        private final @Nullable MethodHandle set;

        public HandleGlobal(ValType type, MethodHandle get, @Nullable MethodHandle set) {
            this.type = type;
            this.get = get.asType(MethodType.methodType(Object.class));
            this.set = set == null ? null : set.asType(MethodType.methodType(void.class, Object.class));
        }

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

        @Override
        public ExternType.@NotNull Global getType() {
            return new ExternType.Global(set != null, type);
        }
    }
}

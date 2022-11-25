package io.github.eutro.wasm2j.embed;

import io.github.eutro.wasm2j.embed.internal.Utils;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

public interface Global {
    @GeneratedAccess
    Object get();

    boolean isMut();

    @GeneratedAccess
    void set(Object value);

    class BoxGlobal implements Global {
        private Object value;

        public BoxGlobal() {
            this(null);
        }

        public BoxGlobal(Object value) {
            this.value = value;
        }

        @Override
        public Object get() {
            return value;
        }

        @Override
        public boolean isMut() {
            return true;
        }

        @Override
        public void set(Object value) {
            this.value = value;
        }
    }

    class HandleGlobal implements Global {
        private final MethodHandle get;
        private final @Nullable MethodHandle set;

        public HandleGlobal(MethodHandle get, @Nullable MethodHandle set) {
            this.get = get.asType(MethodType.methodType(Object.class));
            this.set = set == null ? null : set.asType(MethodType.methodType(void.class, Object.class));
        }

        @GeneratedAccess
        public static HandleGlobal create(MethodHandle get, @Nullable MethodHandle set) {
            return new HandleGlobal(get, set);
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
        public boolean isMut() {
            return set != null;
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
    }
}

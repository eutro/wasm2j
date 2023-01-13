package io.github.eutro.wasm2j.embed;

import io.github.eutro.wasm2j.embed.internal.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

public interface Global extends ExternVal {
    @GeneratedAccess
    Object get();

    @GeneratedAccess
    void set(Object value);

    @Override
    ExternType.@NotNull Global getType();

    class BoxGlobal implements Global {
        private final ValType type;
        private Object value;
        private boolean isMut = true;

        public BoxGlobal(ValType type) {
            this(type, null);
        }

        public BoxGlobal(ValType type, Object value) {
            this.type = type;
            this.value = value;
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
            this.value = value;
        }

        @Override
        public ExternType.@NotNull Global getType() {
            return new ExternType.Global(isMut, type.getOpcode());
        }
    }

    class HandleGlobal implements Global {
        private final byte type;
        private final MethodHandle get;
        private final @Nullable MethodHandle set;

        public HandleGlobal(byte type, MethodHandle get, @Nullable MethodHandle set) {
            this.type = type;
            this.get = get.asType(MethodType.methodType(Object.class));
            this.set = set == null ? null : set.asType(MethodType.methodType(void.class, Object.class));
        }

        @GeneratedAccess
        public static HandleGlobal create(byte type, MethodHandle get, @Nullable MethodHandle set) {
            return new HandleGlobal(type, get, set);
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

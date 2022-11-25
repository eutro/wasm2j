package io.github.eutro.wasm2j.embed;

import io.github.eutro.wasm2j.embed.internal.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Arrays;

public interface Table {
    @GeneratedAccess
    @Nullable Object get(int index);

    @GeneratedAccess
    void set(int index, @Nullable Object value);

    @GeneratedAccess
    int size();

    @GeneratedAccess
    default void init(int dstIdx, int srcIdx, int len, Object[] data) {
        for (int i = 0; i < len; i++) {
            set(dstIdx++, data[srcIdx++]);
        }
    }

    @GeneratedAccess
    default int grow(int growBy, @Nullable Object fillWith) {
        return -1;
    }

    abstract class AbstractArrayTable implements Table {
        private final Integer max;

        public AbstractArrayTable(@Nullable Integer max) {
            this.max = max;
        }

        protected abstract Object @NotNull [] getValues();

        protected abstract void setValues(Object @NotNull [] values);

        @Override
        public @Nullable Object get(int index) {
            return getValues()[index];
        }

        @Override
        public void set(int index, @Nullable Object value) {
            getValues()[index] = value;
        }

        @Override
        public int size() {
            return getValues().length;
        }

        @Override
        public void init(int dstIdx, int srcIdx, int len, Object[] data) {
            System.arraycopy(data, srcIdx, getValues(), dstIdx, len);
        }

        @Override
        public int grow(int growBy, @Nullable Object fillWith) {
            if (growBy < 0) {
                return -1;
            }
            Object[] values = getValues();
            int sz = values.length;
            if (max != null && sz + growBy > max) {
                return -1;
            }
            Object[] newValues;
            try {
                newValues = Arrays.copyOf(values, sz + growBy);
            } catch (OutOfMemoryError ignored) {
                return -1;
            }
            if (fillWith != null) {
                Arrays.fill(newValues, sz, sz + growBy, fillWith);
            }
            setValues(newValues);
            return sz;
        }
    }

    class ArrayTable extends AbstractArrayTable {
        private Object[] values;

        public ArrayTable(int min, @Nullable Integer max) {
            super(max);
            this.values = new Object[min];
        }

        @Override
        protected Object @NotNull [] getValues() {
            return values;
        }

        @Override
        protected void setValues(Object @NotNull [] values) {
            this.values = values;
        }
    }

    class HandleTable implements Table {
        private final MethodHandle get, set, size, grow;

        public HandleTable(MethodHandle get, MethodHandle set, MethodHandle size, MethodHandle grow) {
            this.get = get.asType(MethodType.methodType(Object.class, int.class));
            this.set = set.asType(MethodType.methodType(void.class, int.class, Object.class));
            this.size = size.asType(MethodType.methodType(int.class));
            this.grow = grow.asType(MethodType.methodType(int.class, int.class, Object.class));
        }

        @GeneratedAccess
        public static HandleTable create(MethodHandle get,
                                         MethodHandle set,
                                         MethodHandle size,
                                         MethodHandle grow) {
            return new HandleTable(get, set, size, grow);
        }

        @Override
        public @Nullable Object get(int index) {
            try {
                return get.invokeExact(index);
            } catch (Throwable t) {
                throw Utils.rethrow(t);
            }
        }

        @Override
        public void set(int index, @Nullable Object value) {
            try {
                set.invokeExact(index, value);
            } catch (Throwable t) {
                throw Utils.rethrow(t);
            }
        }

        @Override
        public int size() {
            try {
                return (int) size.invokeExact();
            } catch (Throwable t) {
                throw Utils.rethrow(t);
            }
        }

        @Override
        public int grow(int growBy, @Nullable Object fillWith) {
            try {
                return (int) grow.invokeExact(growBy, fillWith);
            } catch (Throwable t) {
                throw Utils.rethrow(t);
            }
        }
    }
}

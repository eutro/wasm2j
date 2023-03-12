package io.github.eutro.wasm2j.embed;

import io.github.eutro.wasm2j.embed.internal.Utils;
import io.github.eutro.wasm2j.api.types.ExternType;
import io.github.eutro.wasm2j.api.types.ValType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.Arrays;

/**
 * A table extern.
 */
public interface Table extends ExternVal {
    /**
     * Get an element of the table.
     *
     * @param index The index of the element to get.
     * @return The retrieved element.
     */
    @Embedding("table_read")
    @GeneratedAccess
    @Nullable Object get(int index);

    /**
     * Set an element of the table.
     *
     * @param index The index of the element to set.
     * @param value The value to set it to.
     */
    @Embedding("table_write")
    @GeneratedAccess
    void set(int index, @Nullable Object value);

    /**
     * Get the size of the table.
     *
     * @return The size of the table.
     */
    @Embedding("table_size")
    @GeneratedAccess
    int size();

    /**
     * Initialise the table with data.
     *
     * @param dstIdx The index in the table to start at.
     * @param srcIdx The index in the data array to start at.
     * @param len    The number of elements to copy.
     * @param data   The data array.
     */
    @GeneratedAccess
    default void init(int dstIdx, int srcIdx, int len, Object[] data) {
        if (srcIdx + len > data.length || dstIdx + len > size()) {
            throw new IndexOutOfBoundsException();
        }
        for (int i = 0; i < len; i++) {
            set(dstIdx++, data[srcIdx++]);
        }
    }

    /**
     * Grow the table by a number of elements, filling it with the given value.
     * <p>
     * This operation is allowed to fail (and must fail if the upper limit is exceeded),
     * in which case -1 should be returned.
     *
     * @param growBy   The number of elements to grow by.
     * @param fillWith The value to fill new elements with.
     * @return The old size of the table.
     */
    @Embedding("table_grow")
    @GeneratedAccess
    default int grow(int growBy, @Nullable Object fillWith) {
        return -1;
    }

    @Embedding("table_type")
    @NotNull
    @Override
    ExternType.Table getType();

    /**
     * A table with an array as the underlying representation.
     */
    abstract class AbstractArrayTable implements Table {
        /**
         * The minimum number of elements in the table.
         */
        protected final int min;
        /**
         * The maximum number of elements in the table, or null if unbounded.
         */
        protected final @Nullable Integer max;
        /**
         * The component type of the table.
         */
        protected final ValType componentType;

        /**
         * Construct an array table with the given limits and component type.
         *
         * @param min           The minimum.
         * @param max           The maximum.
         * @param componentType The component type.
         */
        protected AbstractArrayTable(int min, @Nullable Integer max, ValType componentType) {
            this.min = min;
            this.max = max;
            this.componentType = componentType;
        }

        /**
         * Get the underlying array of this table.
         *
         * @return The array.
         */
        protected abstract Object @NotNull [] getValues();

        /**
         * Set the underlying array of this table.
         *
         * @param values The array.
         */
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

        @NotNull
        @Override
        public ExternType.Table getType() {
            return new ExternType.Table(
                    new ExternType.Limits(size(), max),
                    componentType
            );
        }
    }

    /**
     * A table which stores its elements as a table in itself.
     */
    class ArrayTable extends AbstractArrayTable {
        private Object[] values;

        /**
         * Construct an array table with the given limits and component type.
         *
         * @param min           The minimum number of elements in the table.
         * @param max           The maximum number of elements in the table, or null if unbounded.
         * @param componentType The component type of the table.
         */
        public ArrayTable(int min, @Nullable Integer max, ValType componentType) {
            super(min, max, componentType);
            // throws if given a primitive type
            this.values = (Object[]) Array.newInstance(componentType.getType(), min);
        }

        /**
         * Construct an array table with the given table type.
         *
         * @param type The table type.
         */
        @Embedding("table_alloc")
        public ArrayTable(ExternType.Table type) {
            this(type.limits.min, type.limits.max, type.componentType);
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

    /**
     * A table which implements its methods by delegating to method handles.
     */
    class HandleTable implements Table {
        private final ExternType.Table type;
        private final MethodHandle get;
        private final MethodHandle set;
        private final MethodHandle size;
        private final MethodHandle grow;

        /**
         * Construct a new handle table with the given types and method implementations.
         *
         * @param type The type of the table.
         * @param get  The {@link #get(int)} implementation.
         * @param set  The {@link #set(int, Object)} implementation.
         * @param size The {@link #size()} implementation.
         * @param grow The {@link #grow(int, Object)} implementation.
         */
        public HandleTable(
                ExternType.Table type,
                MethodHandle get,
                MethodHandle set,
                MethodHandle size,
                MethodHandle grow
        ) {
            this.type = type;
            this.get = get.asType(MethodType.methodType(Object.class, int.class));
            this.set = set.asType(MethodType.methodType(void.class, int.class, Object.class));
            this.size = size.asType(MethodType.methodType(int.class));
            this.grow = grow.asType(MethodType.methodType(int.class, int.class, Object.class));
        }

        /**
         * Calls {@link HandleTable#HandleTable(ExternType.Table, MethodHandle, MethodHandle, MethodHandle, MethodHandle)}.
         *
         * @param type The type of the table.
         * @param get The {@link #get(int)} implementation.
         * @param set The {@link #set(int, Object)} implementation.
         * @param size The {@link #size()} implementation.
         * @param grow The {@link #grow(int, Object)} implementation.
         * @return The new handle table.
         */
        @GeneratedAccess
        public static HandleTable create(ExternType.Table type,
                                         MethodHandle get,
                                         MethodHandle set,
                                         MethodHandle size,
                                         MethodHandle grow) {
            return new HandleTable(type, get, set, size, grow);
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

        @NotNull
        @Override
        public ExternType.Table getType() {
            return new ExternType.Table(
                    new ExternType.Limits(size(), type.limits.max),
                    type.componentType
            );
        }
    }
}

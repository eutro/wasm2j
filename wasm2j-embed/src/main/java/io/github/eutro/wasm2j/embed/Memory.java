package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.Opcodes;
import io.github.eutro.wasm2j.embed.internal.Utils;
import io.github.eutro.wasm2j.core.ops.WasmOps;
import io.github.eutro.wasm2j.api.types.ExternType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;

import java.lang.invoke.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.EnumMap;
import java.util.Map;

import static io.github.eutro.jwasm.Opcodes.PAGE_SIZE;
import static org.objectweb.asm.Opcodes.H_INVOKESTATIC;

/**
 * A memory extern.
 */
public interface Memory extends ExternVal {
    /**
     * Something that has a WebAssembly single-byte opcode.
     */
    interface HasOpcode {
        /**
         * Get the single-byte opcode of this.
         *
         * @return The opcode.
         */
        byte opcode();
    }

    /**
     * A type of load instruction.
     */
    enum LoadMode implements HasOpcode {
        /**
         * Read 4 bytes, interpret as a little-endian integer.
         */
        I32_LOAD(Opcodes.I32_LOAD),
        /**
         * Read 8 bytes, interpret as a little-endian long.
         */
        I64_LOAD(Opcodes.I64_LOAD),
        /**
         * Read 4 bytes, interpret as a little-endian float.
         */
        F32_LOAD(Opcodes.F32_LOAD),
        /**
         * Read 8 bytes, interpret as a little-endian double.
         */
        F64_LOAD(Opcodes.F64_LOAD),
        /**
         * Read 1 byte, interpret as an integer and sign extend to a 4 byte integer.
         */
        I32_LOAD8_S(Opcodes.I32_LOAD8_S),
        /**
         * Read 1 byte, interpret as an integer and zero extend to a 4 byte integer.
         */
        I32_LOAD8_U(Opcodes.I32_LOAD8_U),
        /**
         * Read 2 bytes, interpret as a little-endian integer and sign extend to a 4 byte integer.
         */
        I32_LOAD16_S(Opcodes.I32_LOAD16_S),
        /**
         * Read 2 bytes, interpret as a little-endian integer and zero extend to a 4 byte integer.
         */
        I32_LOAD16_U(Opcodes.I32_LOAD16_U),
        /**
         * Read 1 byte, interpret as an integer and sign extend to an 8 byte long.
         */
        I64_LOAD8_S(Opcodes.I64_LOAD8_S),
        /**
         * Read 1 byte, interpret as an integer and zero extend to an 8 byte long.
         */
        I64_LOAD8_U(Opcodes.I64_LOAD8_U),
        /**
         * Read 2 bytes, interpret as a little-endian integer and sign extend to an 8 byte long.
         */
        I64_LOAD16_S(Opcodes.I64_LOAD16_S),
        /**
         * Read 2 bytes, interpret as a little-endian integer and zero extend to an 8 byte long.
         */
        I64_LOAD16_U(Opcodes.I64_LOAD16_U),
        /**
         * Read 4 bytes, interpret as a little-endian integer and sign extend to an 8 byte long.
         */
        I64_LOAD32_S(Opcodes.I64_LOAD32_S),
        /**
         * Read 4 bytes, interpret as a little-endian integer and zero extend to an 8 byte long.
         */
        I64_LOAD32_U(Opcodes.I64_LOAD32_U),
        ;

        private final byte opcode;

        LoadMode(byte opcode) {
            this.opcode = opcode;
        }

        /**
         * Get the load mode of a specific opcode.
         *
         * @param opcode The opcode.
         * @return The mode.
         */
        public static LoadMode fromOpcode(byte opcode) {
            switch (opcode) {
                // @formatter:off
                case Opcodes.I32_LOAD: return I32_LOAD;
                case Opcodes.I64_LOAD: return I64_LOAD;
                case Opcodes.F32_LOAD: return F32_LOAD;
                case Opcodes.F64_LOAD: return F64_LOAD;
                case Opcodes.I32_LOAD8_S: return I32_LOAD8_S;
                case Opcodes.I32_LOAD8_U: return I32_LOAD8_U;
                case Opcodes.I32_LOAD16_S: return I32_LOAD16_S;
                case Opcodes.I32_LOAD16_U: return I32_LOAD16_U;
                case Opcodes.I64_LOAD8_S: return I64_LOAD8_S;
                case Opcodes.I64_LOAD8_U: return I64_LOAD8_U;
                case Opcodes.I64_LOAD16_S: return I64_LOAD16_S;
                case Opcodes.I64_LOAD16_U: return I64_LOAD16_U;
                case Opcodes.I64_LOAD32_S: return I64_LOAD32_S;
                case Opcodes.I64_LOAD32_U: return I64_LOAD32_U;
                // @formatter:on
                default:
                    throw new IllegalArgumentException();
            }
        }

        @Override
        public byte opcode() {
            return opcode;
        }
    }

    /**
     * A type of store instruction.
     */
    enum StoreMode implements HasOpcode {
        /**
         * Store an integer as 4 little-endian bytes.
         */
        I32_STORE(Opcodes.I32_STORE),
        /**
         * Store a long as 8 little-endian bytes.
         */
        I64_STORE(Opcodes.I64_STORE),
        /**
         * Store a float as 4 little-endian bytes.
         */
        F32_STORE(Opcodes.F32_STORE),
        /**
         * Store a double as 8 little-endian bytes.
         */
        F64_STORE(Opcodes.F64_STORE),
        /**
         * Store the least-significant byte of an integer.
         */
        I32_STORE8(Opcodes.I32_STORE8),
        /**
         * Store the least-significant 2 bytes in little-endian order of an integer.
         */
        I32_STORE16(Opcodes.I32_STORE16),
        /**
         * Store the least-significant byte of a long.
         */
        I64_STORE8(Opcodes.I64_STORE8),
        /**
         * Store the least-significant 2 bytes in little-endian order of a long.
         */
        I64_STORE16(Opcodes.I64_STORE16),
        /**
         * Store the least-significant 4 bytes in little-endian order of a long.
         */
        I64_STORE32(Opcodes.I64_STORE32),
        ;

        private final byte opcode;

        StoreMode(byte opcode) {
            this.opcode = opcode;
        }

        /**
         * Get the store mode of a specific opcode.
         *
         * @param opcode The opcode.
         * @return The mode.
         */
        public static StoreMode fromOpcode(byte opcode) {
            switch (opcode) {
                // @formatter:off
                case Opcodes.I32_STORE: return I32_STORE;
                case Opcodes.I64_STORE: return I64_STORE;
                case Opcodes.F32_STORE: return F32_STORE;
                case Opcodes.F64_STORE: return F64_STORE;
                case Opcodes.I32_STORE8: return I32_STORE8;
                case Opcodes.I32_STORE16: return I32_STORE16;
                case Opcodes.I64_STORE8: return I64_STORE8;
                case Opcodes.I64_STORE16: return I64_STORE16;
                case Opcodes.I64_STORE32: return I64_STORE32;
                // @formatter:on
                default:
                    throw new IllegalArgumentException();
            }
        }

        @Override
        public byte opcode() {
            return opcode;
        }
    }

    /**
     * Get a method handle which loads with the specific mode.
     * <p>
     * The handle should accept one integer argument: the memory address; it should return the appropriate type
     * according to the mode.
     *
     * @param mode The mode.
     * @return The handle.
     */
    @GeneratedAccess
    MethodHandle loadHandle(LoadMode mode);

    /**
     * Get a method handle which stores with the specific mode.
     * <p>
     * The handle should accept an integer argument: the memory address, and the value, dependent upon the mode;
     * it should return void.
     *
     * @param mode The mode.
     * @return The handle.
     */
    @GeneratedAccess
    MethodHandle storeHandle(StoreMode mode);

    /**
     * Get the size of the memory, in pages.
     *
     * @return The size.
     * @see Opcodes#PAGE_SIZE
     */
    @Embedding("mem_size")
    @GeneratedAccess
    int size();

    /**
     * Grow the memory by a number of pages, filling new pages with 0.
     * <p>
     * This operation is allowed to fail (and must fail if the upper limit is exceeded),
     * in which case -1 should be returned.
     *
     * @param growByPages The number of pages to grow by, interpreted as an unsigned integer.
     * @return The old size, or -1 if the memory was not grown.
     * @see Opcodes#PAGE_SIZE
     */
    @Embedding("mem_grow")
    @GeneratedAccess
    int grow(int growByPages);

    /**
     * Initialise the memory with the given data.
     *
     * @param dstIdx The destination address in this memory.
     * @param srcIdx The start address in {@code buf}.
     * @param len    The number of bytes to copy.
     * @param buf    The buffer of data.
     */
    @GeneratedAccess
    void init(int dstIdx, int srcIdx, int len, ByteBuffer buf);

    @Embedding("mem_type")
    @Override
    ExternType.@NotNull Mem getType();

    /**
     * Read a byte at the given address.
     * <p>
     * Using {@link #loadHandle(LoadMode)} and storing the handle should be preferred,
     * since it is quicker and allows for more reading options.
     *
     * @param addr The address to read.
     * @return The byte read.
     */
    @Embedding("mem_read")
    default byte read(int addr) {
        try {
            return (byte) (int) loadHandle(Memory.LoadMode.I32_LOAD8_S).invokeExact(addr);
        } catch (Throwable t) {
            throw Utils.rethrow(t);
        }
    }

    /**
     * Write a byte to the given address.
     * <p>
     * Using {@link #storeHandle(StoreMode)} and storing the handle should be preferred,
     * since it is quicker and allows for more writing options.
     *
     * @param addr  The address to read.
     * @param value The byte to write.
     */
    @Embedding("mem_write")
    default void write(int addr, byte value) {
        try {
            storeHandle(Memory.StoreMode.I32_STORE8).invokeExact(addr, value);
        } catch (Throwable t) {
            throw Utils.rethrow(t);
        }
    }

    /**
     * Create a new memory with the given type.
     * <p>
     * This currently allocates a {@link ByteBufferMemory}.
     *
     * @param type The type.
     * @return The new memory.
     */
    @Embedding("mem_alloc")
    static Memory alloc(ExternType.Mem type) {
        return new ByteBufferMemory(type);
    }

    /**
     * A memory which delegates its calls to the provided method handles.
     */
    class HandleMemory implements Memory {
        private final @Nullable Integer max;
        private final MethodHandle
                loadHandle,
                storeHandle,
                size,
                grow,
                init;

        private HandleMemory(
                @Nullable Integer max,
                MethodHandle loadHandle,
                MethodHandle storeHandle,
                MethodHandle size,
                MethodHandle grow,
                MethodHandle init
        ) {
            this.max = max;
            this.loadHandle = loadHandle;
            this.storeHandle = storeHandle;
            this.size = size;
            this.grow = grow;
            this.init = init;
        }

        /**
         * Create a handle memory with the given maximum and handles.
         *
         * @param max         The maximum number of pages, or null if unbounded.
         * @param loadHandle  The {@link #storeHandle(StoreMode)} implementation.
         * @param storeHandle The {@link #storeHandle(StoreMode)} implementation.
         * @param size        The {@link #size()} implementation.
         * @param grow        The {@link #grow(int)} implementation.
         * @param init        The {@link #init(int, int, int, ByteBuffer)} implementation.
         * @return The new memory.
         */
        @GeneratedAccess
        public static HandleMemory create(
                @Nullable Integer max,
                MethodHandle loadHandle,
                MethodHandle storeHandle,
                MethodHandle size,
                MethodHandle grow,
                MethodHandle init
        ) {
            return new HandleMemory(max, loadHandle, storeHandle, size, grow, init);
        }

        @Override
        public MethodHandle loadHandle(LoadMode mode) {
            try {
                return (MethodHandle) loadHandle.invokeExact(mode);
            } catch (Throwable t) {
                throw Utils.rethrow(t);
            }
        }

        @Override
        public MethodHandle storeHandle(StoreMode mode) {
            try {
                return (MethodHandle) storeHandle.invokeExact(mode);
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
        public int grow(int growByPages) {
            try {
                return (int) grow.invokeExact(growByPages);
            } catch (Throwable t) {
                throw Utils.rethrow(t);
            }
        }

        @Override
        public void init(int dstIdx, int srcIdx, int len, ByteBuffer buf) {
            try {
                init.invokeExact(dstIdx, srcIdx, len, buf);
            } catch (Throwable t) {
                throw Utils.rethrow(t);
            }
        }

        @Override
        public ExternType.@NotNull Mem getType() {
            return new ExternType.Mem(new ExternType.Limits(size(), max));
        }
    }

    /**
     * Bootstrap methods for memory load and store {@code invokedynamic} instructions.
     */
    class Bootstrap {
        /**
         * An ASM {@link Handle} to {@link #bootstrapMemoryInsn(MethodHandles.Lookup, String, MethodType, int)}.
         */
        public static final Handle BOOTSTRAP_HANDLE;
        private static final MethodHandle LOAD_INIT;
        private static final MethodHandle STORE_INIT;

        static {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            try {
                BOOTSTRAP_HANDLE = new Handle(
                        H_INVOKESTATIC,
                        Type.getInternalName(Bootstrap.class),
                        "bootstrapMemoryInsn",
                        Type.getMethodDescriptor(Bootstrap.class.getMethod("bootstrapMemoryInsn",
                                MethodHandles.Lookup.class,
                                String.class,
                                MethodType.class,
                                int.class)),
                        false
                );
                LOAD_INIT = lookup.findStatic(Bootstrap.class, "loadInit",
                        MethodType.methodType(Object.class, MutableCallSite.class,
                                int.class, MethodType.class, Memory.class, int.class));
                STORE_INIT = lookup.findStatic(Bootstrap.class, "storeInit",
                        MethodType.methodType(void.class, MutableCallSite.class, int.class,
                                MethodType.class, Memory.class, int.class, Object.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * The bootstrap method for memory instructions.
         *
         * @param ignoredCaller The caller, ignored.
         * @param invokedName   The name specified in the {@code invokedynamic} instruction.
         * @param invokedType   The type specified in the instruction.
         * @param modeOrdinal   The ordinal of the load or store mode.
         * @return The call site.
         */
        public static CallSite bootstrapMemoryInsn(
                MethodHandles.Lookup ignoredCaller,
                String invokedName,
                MethodType invokedType,
                int modeOrdinal
        ) {
            MutableCallSite site = new MutableCallSite(invokedType);
            MethodHandle handle;
            if ("load".equals(invokedName)) {
                handle = LOAD_INIT;
            } else if ("store".equals(invokedName)) {
                handle = STORE_INIT;
            } else {
                throw new IllegalArgumentException();
            }
            MethodHandle inserted = MethodHandles.insertArguments(handle, 0, site, modeOrdinal, invokedType);
            site.setTarget(inserted.asType(invokedType));
            return site;
        }

        private static Object loadInit(MutableCallSite callSite, int typeOrdinal, MethodType type, Memory mem, int addr)
                throws Throwable {
            MethodHandle handle = MethodHandles.dropArguments(
                    mem.loadHandle(LoadMode.values()[typeOrdinal]),
                    0,
                    Memory.class
            ).asType(type);
            callSite.setTarget(handle);
            return handle.invoke(mem, addr);
        }

        private static void storeInit(MutableCallSite callSite, int typeOrdinal, MethodType type, Memory mem, int addr, Object value)
                throws Throwable {
            MethodHandle handle = MethodHandles.dropArguments(
                    mem.storeHandle(StoreMode.values()[typeOrdinal]),
                    0,
                    Memory.class
            ).asType(type);
            callSite.setTarget(handle);
            handle.invoke(mem, addr, value);
        }
    }

    /**
     * A memory implemented using a {@link ByteBuffer}.
     */
    class ByteBufferMemory implements Memory {
        private static final EnumMap<WasmOps.DerefType.ExtType, MethodHandle> EXT_HANDLES =
                new EnumMap<>(WasmOps.DerefType.ExtType.class);
        private static final EnumMap<WasmOps.DerefType.LoadType, MethodHandle> LOAD_HANDLES =
                new EnumMap<>(WasmOps.DerefType.LoadType.class);

        private static final EnumMap<WasmOps.StoreType, MethodHandle> STORE_HANDLES =
                new EnumMap<>(WasmOps.StoreType.class);

        private static final MethodHandle GET_BUF;

        static {
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();

                MethodHandle I2L = MethodHandles.explicitCastArguments(MethodHandles.identity(int.class),
                        MethodType.methodType(int.class, long.class));
                EXT_HANDLES.put(WasmOps.DerefType.ExtType.NOEXT, null);
                EXT_HANDLES.put(WasmOps.DerefType.ExtType.S8_32, null);
                EXT_HANDLES.put(WasmOps.DerefType.ExtType.S16_32, null);
                EXT_HANDLES.put(WasmOps.DerefType.ExtType.S8_64, I2L);
                EXT_HANDLES.put(WasmOps.DerefType.ExtType.S16_64, I2L);
                EXT_HANDLES.put(WasmOps.DerefType.ExtType.S32_64, I2L);
                EXT_HANDLES.put(WasmOps.DerefType.ExtType.U8_32,
                        lookup.findStatic(Byte.class, "toUnsignedInt", MethodType.methodType(int.class, byte.class)));
                EXT_HANDLES.put(WasmOps.DerefType.ExtType.U16_32,
                        lookup.findStatic(Short.class, "toUnsignedInt", MethodType.methodType(int.class, short.class)));
                EXT_HANDLES.put(WasmOps.DerefType.ExtType.U8_64,
                        lookup.findStatic(Byte.class, "toUnsignedLong", MethodType.methodType(long.class, byte.class)));
                EXT_HANDLES.put(WasmOps.DerefType.ExtType.U16_64,
                        lookup.findStatic(Short.class, "toUnsignedLong", MethodType.methodType(long.class, short.class)));
                EXT_HANDLES.put(WasmOps.DerefType.ExtType.U32_64,
                        lookup.findStatic(Integer.class, "toUnsignedLong", MethodType.methodType(long.class, int.class)));

                LOAD_HANDLES.put(WasmOps.DerefType.LoadType.I8,
                        lookup.findVirtual(ByteBuffer.class, "get", MethodType.methodType(byte.class, int.class)));
                LOAD_HANDLES.put(WasmOps.DerefType.LoadType.I16,
                        lookup.findVirtual(ByteBuffer.class, "getShort", MethodType.methodType(short.class, int.class)));
                LOAD_HANDLES.put(WasmOps.DerefType.LoadType.I32,
                        lookup.findVirtual(ByteBuffer.class, "getInt", MethodType.methodType(int.class, int.class)));
                LOAD_HANDLES.put(WasmOps.DerefType.LoadType.I64,
                        lookup.findVirtual(ByteBuffer.class, "getLong", MethodType.methodType(long.class, int.class)));
                LOAD_HANDLES.put(WasmOps.DerefType.LoadType.F32,
                        lookup.findVirtual(ByteBuffer.class, "getFloat", MethodType.methodType(float.class, int.class)));
                LOAD_HANDLES.put(WasmOps.DerefType.LoadType.F64,
                        lookup.findVirtual(ByteBuffer.class, "getDouble", MethodType.methodType(double.class, int.class)));

                STORE_HANDLES.put(WasmOps.StoreType.F32,
                        lookup.findVirtual(ByteBuffer.class, "putFloat", MethodType
                                .methodType(ByteBuffer.class, int.class, float.class)));
                STORE_HANDLES.put(WasmOps.StoreType.F64,
                        lookup.findVirtual(ByteBuffer.class, "putDouble", MethodType
                                .methodType(ByteBuffer.class, int.class, double.class)));
                STORE_HANDLES.put(WasmOps.StoreType.I32_8,
                        lookup.findVirtual(ByteBuffer.class, "put", MethodType
                                .methodType(ByteBuffer.class, int.class, byte.class)));
                STORE_HANDLES.put(WasmOps.StoreType.I32_16,
                        lookup.findVirtual(ByteBuffer.class, "putShort", MethodType
                                .methodType(ByteBuffer.class, int.class, short.class)));
                STORE_HANDLES.put(WasmOps.StoreType.I32,
                        lookup.findVirtual(ByteBuffer.class, "putInt", MethodType
                                .methodType(ByteBuffer.class, int.class, int.class)));
                STORE_HANDLES.put(WasmOps.StoreType.I64_8,
                        MethodHandles.explicitCastArguments(
                                lookup.findVirtual(ByteBuffer.class, "put", MethodType
                                        .methodType(ByteBuffer.class, int.class, byte.class)),
                                MethodType.methodType(void.class, ByteBuffer.class, int.class, long.class)));
                STORE_HANDLES.put(WasmOps.StoreType.I64_16,
                        MethodHandles.explicitCastArguments(
                                lookup.findVirtual(ByteBuffer.class, "putShort", MethodType
                                        .methodType(ByteBuffer.class, int.class, short.class)),
                                MethodType.methodType(void.class, ByteBuffer.class, int.class, long.class)));
                STORE_HANDLES.put(WasmOps.StoreType.I64_32,
                        MethodHandles.explicitCastArguments(
                                lookup.findVirtual(ByteBuffer.class, "putInt", MethodType
                                        .methodType(ByteBuffer.class, int.class, int.class)),
                                MethodType.methodType(void.class, ByteBuffer.class, int.class, long.class)));
                STORE_HANDLES.put(WasmOps.StoreType.I64,
                        lookup.findVirtual(ByteBuffer.class, "putLong", MethodType
                                .methodType(ByteBuffer.class, int.class, long.class)));

                GET_BUF = lookup.findGetter(ByteBufferMemory.class, "buf", ByteBuffer.class);

                for (Map.Entry<WasmOps.StoreType, MethodHandle> entry : STORE_HANDLES.entrySet()) {
                    entry.setValue(entry.getValue().asType(entry.getValue().type().changeReturnType(void.class)));
                }
            } catch (IllegalAccessException | NoSuchMethodException | NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        private ByteBuffer buf;
        private final @Nullable Integer max;

        /**
         * Create a new byte buffer memory with the given limits.
         *
         * @param min The minimum number of pages.
         * @param max The maximum number of pages, or null if unbounded.
         */
        public ByteBufferMemory(int min, @Nullable Integer max) {
            this.max = max;
            this.buf = ByteBuffer.allocateDirect(min * PAGE_SIZE)
                    .order(ByteOrder.LITTLE_ENDIAN);
        }

        /**
         * Create a new byte buffer memory with the given type.
         *
         * @param type The type.
         */
        public ByteBufferMemory(ExternType.Mem type) {
            this(type.limits.min, type.limits.max);
        }

        @Override
        public MethodHandle loadHandle(LoadMode mode) {
            WasmOps.DerefType dTy = WasmOps.DerefType.fromOpcode(mode.opcode());
            MethodHandle handle = MethodHandles.collectArguments(LOAD_HANDLES.get(dTy.load),
                    0, GET_BUF.bindTo(this));
            MethodHandle ext = EXT_HANDLES.get(dTy.ext);
            if (ext != null) handle = MethodHandles.filterReturnValue(handle, ext);
            return handle;
        }

        @Override
        public MethodHandle storeHandle(StoreMode mode) {
            WasmOps.StoreType sTy = WasmOps.StoreType.fromOpcode(mode.opcode);
            return STORE_HANDLES.get(sTy).bindTo(buf);
        }

        @Override
        public int size() {
            return buf.capacity() / PAGE_SIZE;
        }

        @Override
        public int grow(int growByPages) {
            if (growByPages < 0) {
                return -1;
            }
            int sz = buf.capacity() / PAGE_SIZE;
            if (max != null && sz + growByPages > max) {
                return -1;
            }
            ByteBuffer newBuf;
            try {
                newBuf = ByteBuffer.allocateDirect(buf.capacity() + growByPages * PAGE_SIZE)
                        .order(ByteOrder.LITTLE_ENDIAN);
            } catch (OutOfMemoryError ignored) {
                return -1;
            }
            newBuf.duplicate().put(buf.duplicate());
            buf = newBuf;
            return sz;
        }

        @Override
        public void init(int dstIdx, int srcIdx, int len, ByteBuffer buf) {
            ByteBuffer thisSliced = this.buf.slice(), otherSliced = buf.slice();
            thisSliced.position(dstIdx);
            otherSliced.position(srcIdx).limit(srcIdx + len);
            thisSliced.put(otherSliced);
        }

        @Override
        public ExternType.@NotNull Mem getType() {
            return new ExternType.Mem(new ExternType.Limits(size(), max));
        }
    }
}

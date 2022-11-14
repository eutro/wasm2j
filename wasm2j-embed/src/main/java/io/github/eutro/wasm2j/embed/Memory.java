package io.github.eutro.wasm2j.embed;

import io.github.eutro.wasm2j.embed.internal.Utils;

import java.lang.invoke.MethodHandle;

public interface Memory {
    MethodHandle loadHandle(int offset);

    MethodHandle storeHandle(int offset);

    int size();

    int grow(int growByPages);

    class HandleMemory implements Memory {
        private final MethodHandle
                loadHandle,
                storeHandle,
                size,
                grow;

        public HandleMemory(
                MethodHandle loadHandle,
                MethodHandle storeHandle,
                MethodHandle size,
                MethodHandle grow
        ) {
            this.loadHandle = loadHandle;
            this.storeHandle = storeHandle;
            this.size = size;
            this.grow = grow;
        }

        @Override
        public MethodHandle loadHandle(int offset) {
            try {
                return (MethodHandle) loadHandle.invokeExact(offset);
            } catch (Throwable t) {
                throw Utils.rethrow(t);
            }
        }

        @Override
        public MethodHandle storeHandle(int offset) {
            try {
                return (MethodHandle) storeHandle.invokeExact(offset);
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
    }
}

package io.github.eutro.wasm2j.embed;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;

public interface Func extends ExternVal {
    MethodHandle handle();

    @Override
    ExternType.@NotNull Func getType();

    class HandleFunc implements Func {
        private final ExternType.Func type;
        private final MethodHandle handle;

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

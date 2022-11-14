package io.github.eutro.wasm2j.embed.internal;

public class Utils {
    public static RuntimeException rethrow(Throwable t) {
        if (t instanceof RuntimeException) {
            throw (RuntimeException) t;
        } else if (t instanceof Error) {
            throw (Error) t;
        } else {
            throw new RuntimeException(t);
        }
    }
}

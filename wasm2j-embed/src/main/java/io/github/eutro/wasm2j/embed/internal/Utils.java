package io.github.eutro.wasm2j.embed.internal;

/**
 * Miscellaneous utilities.
 */
public class Utils {
    /**
     * Rethrow the throwable. If it can be thrown unchecked, it will be, otherwise
     * it will be wrapped in a {@link RuntimeException}.
     * <p>
     * This method does not return, but it is typed to return a runtime exception so the calling
     * code can write:
     *
     * <pre>{@code
     * try {
     *     // ...
     * } catch (Throwable t) {
     *     throw rethrow(t);
     * }
     * }</pre>
     *
     * @param t The throwable.
     * @return No it doesn't.
     */
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

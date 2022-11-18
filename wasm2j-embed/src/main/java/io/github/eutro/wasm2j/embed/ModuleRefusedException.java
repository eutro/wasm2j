package io.github.eutro.wasm2j.embed;

/**
 * Thrown when instantiation of a module fails (is refused) for a reason other than it being invalid.
 * <p>
 * This might happen when a method is too large, for example.
 */
public class ModuleRefusedException extends RuntimeException {
    public ModuleRefusedException(Throwable cause) {
        super(cause);
    }
}

package io.github.eutro.wasm2j.api.events;

import io.github.eutro.wasm2j.api.ModuleCompilation;
import io.github.eutro.wasm2j.core.passes.opts.Stackify;
import io.github.eutro.wasm2j.core.ssa.JClass;
import org.jetbrains.annotations.NotNull;

/**
 * Fired just before the Java IR is converted to Java bytecode.
 *
 * @see ModuleCompilation
 */
public class JavaPreemitEvent implements ModuleCompileEvent {
    /**
     * The Java IR being compiled.
     * <p>
     * Functions will <i>not</i> be in SSA form anymore, registers will have been
     * allocated and {@link Stackify stackification} completed.
     */
    @NotNull
    public JClass jir;

    /**
     * Construct a new Java pre-emit event with the given IR.
     *
     * @param jir The Java IR.
     */
    public JavaPreemitEvent(@NotNull JClass jir) {
        this.jir = jir;
    }
}

package io.github.eutro.wasm2j.api.events;

import io.github.eutro.wasm2j.api.ModuleCompilation;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;

/**
 * Fired when a class should be emitted.
 *
 * @see ModuleCompilation
 */
public class EmitClassEvent implements ModuleCompileEvent, CancellableEvent {
    /**
     * The class to be emitted.
     */
    @NotNull
    public ClassNode classNode;
    private boolean cancelled = false;

    /**
     * Construct a new class emit event.
     *
     * @param classNode The class to emit.
     */
    public EmitClassEvent(@NotNull ClassNode classNode) {
        this.classNode = classNode;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void cancel() {
        cancelled = true;
    }
}

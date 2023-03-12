package io.github.eutro.wasm2j.api.events;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;

public class EmitClassEvent implements ModuleCompileEvent, CancellableEvent {
    @NotNull
    public ClassNode classNode;
    private boolean cancelled = false;

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

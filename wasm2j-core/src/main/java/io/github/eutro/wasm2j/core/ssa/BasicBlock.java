package io.github.eutro.wasm2j.core.ssa;

import io.github.eutro.wasm2j.core.ext.CommonExts;
import io.github.eutro.wasm2j.core.ext.Ext;
import io.github.eutro.wasm2j.core.ext.ExtContainer;
import io.github.eutro.wasm2j.core.ext.ExtHolder;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A basic block, encapsulating a list of {@link Effect} instructions,
 * followed by exactly one {@link Control} instruction at the end.
 */
public final class BasicBlock extends ExtHolder {
    private final TrackedList<Effect> effects = new TrackedList<Effect>(new ArrayList<>()) {
        @Override
        protected void onAdded(Effect elt) {
            registerWithThis(elt);
        }

        @Override
        protected void onRemoved(Effect elt) {
        }
    };
    private Control control;

    /**
     * Format this block as a jump target, for debugging.
     *
     * @return The jump target string.
     */
    public String toTargetString() {
        return String.format("@%08x", System.identityHashCode(this));
    }

    BasicBlock() {}

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(toTargetString()).append("\n{\n");
        for (Effect effect : getEffects()) {
            sb.append(' ').append(effect).append('\n');
        }
        sb.append(' ').append(getControl());
        sb.append("\n}");
        return sb.toString();
    }

    private <T extends ExtContainer> T registerWithThis(T extable) {
        if (extable != null) {
            extable.attachExt(CommonExts.OWNING_BLOCK, this);
        }
        return extable;
    }

    /**
     * Get the list of {@link Effect effects} in this basic block.
     *
     * @return The list.
     */
    public List<Effect> getEffects() {
        return effects;
    }

    /**
     * Add an {@link Effect effect} to the end of this basic block.
     *
     * @param effect The effect to add.
     */
    public void addEffect(Effect effect) {
        effects.add(registerWithThis(effect));
    }

    /**
     * Get the control instruction of this block.
     *
     * @return The control instruction.
     */
    public Control getControl() {
        return control;
    }

    /**
     * Set the control instruction of this block.
     *
     * @param control The control instruction.
     */
    public void setControl(Control control) {
        this.control = registerWithThis(control);
    }

    // exts
    private Function owner = null;

    @SuppressWarnings("unchecked")
    @Override
    public <T> @Nullable T getNullable(Ext<T> ext) {
        if (ext == CommonExts.OWNING_FUNCTION) {
            return (T) owner;
        }
        return super.getNullable(ext);
    }

    @Override
    public <T> void attachExt(Ext<T> ext, T value) {
        if (ext == CommonExts.OWNING_FUNCTION) {
            owner = (Function) value;
            return;
        }
        super.attachExt(ext, value);
    }

    @Override
    public <T> void removeExt(Ext<T> ext) {
        if (ext == CommonExts.OWNING_FUNCTION) {
            owner = null;
            return;
        }
        super.removeExt(ext);
    }
}

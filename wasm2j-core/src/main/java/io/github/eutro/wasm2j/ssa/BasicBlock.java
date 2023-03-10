package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.*;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

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

    public String toTargetString() {
        return String.format("@%08x", System.identityHashCode(this));
    }

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

    public List<Effect> getEffects() {
        return effects;
    }

    public void addEffect(Effect effect) {
        effects.add(registerWithThis(effect));
    }

    public void setEffects(List<Effect> effects) {
        this.effects.setViewed(effects);
    }

    public Control getControl() {
        return control;
    }

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

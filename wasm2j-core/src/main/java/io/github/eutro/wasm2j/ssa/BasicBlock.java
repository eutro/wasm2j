package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.ExtHolder;
import io.github.eutro.wasm2j.ext.TrackedList;
import io.github.eutro.wasm2j.ext.ExtContainer;

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
}

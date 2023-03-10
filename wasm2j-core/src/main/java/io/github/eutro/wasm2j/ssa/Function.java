package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.*;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.*;

public final class Function extends ExtHolder {
    public final List<BasicBlock> blocks = new TrackedList<BasicBlock>(new ArrayList<>()) {
        @Override
        protected void onAdded(BasicBlock elt) {
            elt.attachExt(CommonExts.OWNING_FUNCTION, Function.this);
        }

        @Override
        protected void onRemoved(BasicBlock elt) {
            elt.removeExt(CommonExts.OWNING_FUNCTION);
        }
    }; // [0] is entry

    public static boolean UNIQUE_VAR_NAMES = System.getenv("WASM2J_UNIQUE_VAR_NAMES") != null;

    // if we hold on to them forever we can easily OOM, but we don't really
    // care about names except for debugging, so let the JVM clear it up if it has to
    private SoftReference<Map<String, Integer>> varsRef = UNIQUE_VAR_NAMES ? new SoftReference<>(new HashMap<>()) : null;

    public void clearVarNames() {
        if (UNIQUE_VAR_NAMES) {
            varsRef = new SoftReference<>(new HashMap<>());
        }
    }

    public Var newVar(String name, int indexHint) {
        if (!UNIQUE_VAR_NAMES) {
            return new Var(name, indexHint);
        }
        Var var;
        Map<String, Integer> vars = varsRef.get();
        if (vars == null) {
            vars = new HashMap<>();
            varsRef = new SoftReference<>(vars);
        }
        if (vars.containsKey(name)) {
            var = new Var(name, Math.max(indexHint, vars.get(name)));
            vars.computeIfPresent(name, ($, i) -> Math.max(indexHint, i) + 1);
        } else {
            var = new Var(name, indexHint);
            vars.put(name, 0);
        }
        return var;
    }

    public Var newVar(String name) {
        return newVar(name, 0);
    }

    public Var newVarFmt(@PrintFormat String fmt, Object... args) {
        if (!UNIQUE_VAR_NAMES) {
            return newVar(fmt);
        }
        return newVar(String.format(fmt, args));
    }

    public BasicBlock newBb() {
        BasicBlock bb = new BasicBlock();
        blocks.add(bb);
        return bb;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("fn() {\n");
        for (BasicBlock block : blocks) {
            sb.append(block).append('\n');
        }
        sb.append("}");
        return sb.toString();
    }

    // exts
    private MetadataState metaState = new MetadataState();

    @Override
    public <T> void attachExt(Ext<T> ext, T value) {
        if (ext == CommonExts.METADATA_STATE) {
            metaState = (MetadataState) value;
            return;
        }
        super.attachExt(ext, value);
    }

    @Override
    public <T> void removeExt(Ext<T> ext) {
        if (ext == CommonExts.METADATA_STATE) {
            metaState = null;
            return;
        }
        super.removeExt(ext);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> @Nullable T getNullable(Ext<T> ext) {
        if (ext == CommonExts.METADATA_STATE) {
            return (T) metaState;
        }
        return super.getNullable(ext);
    }
}

package io.github.eutro.wasm2j.core.ssa;

import io.github.eutro.wasm2j.core.ext.CommonExts;
import io.github.eutro.wasm2j.core.ext.Ext;
import io.github.eutro.wasm2j.core.ext.ExtHolder;
import io.github.eutro.wasm2j.core.ext.MetadataState;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A function, encapsulating a list of {@link BasicBlock basic blocks}.
 */
public final class Function extends ExtHolder {
    /**
     * The list of basic blocks in this function. The first element is the root block,
     * and must be added when this function is first constructed.
     */
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

    /**
     * Whether variable name collisions should be computed. This is useful for debugging,
     * so variables display differently.
     */
    public static boolean UNIQUE_VAR_NAMES = System.getenv("WASM2J_UNIQUE_VAR_NAMES") != null;

    // if we hold on to them forever we can easily OOM, but we don't really
    // care about names except for debugging, so let the JVM clear it up if it has to
    private SoftReference<Map<String, Integer>> varsRef = UNIQUE_VAR_NAMES ? new SoftReference<>(new HashMap<>()) : null;

    /**
     * Clear the variable name counters. Only relevant if {@link #UNIQUE_VAR_NAMES} is true.
     */
    public void clearVarNames() {
        if (UNIQUE_VAR_NAMES) {
            varsRef = new SoftReference<>(new HashMap<>());
        }
    }

    /**
     * Create a new variable with the given name.
     *
     * @param name      The name.
     * @param indexHint The minimum value of the variable index.
     * @return The new variable.
     */
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

    /**
     * Create a new variable with the given name.
     *
     * @param name The name.
     * @return The new variable.
     */
    public Var newVar(String name) {
        return newVar(name, 0);
    }

    /**
     * Create a new variable with a formatted name.
     * <p>
     * The string will not be formatted if {@link #UNIQUE_VAR_NAMES} is false.
     *
     * @param fmt  The format string.
     * @param args The format arguments.
     * @return The new variable.
     */
    public Var newVarFmt(@PrintFormat String fmt, Object... args) {
        if (!UNIQUE_VAR_NAMES) {
            return newVar(fmt);
        }
        return newVar(String.format(fmt, args));
    }

    /**
     * Creates a new basic block in this function.
     *
     * @return The new basic block.
     */
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

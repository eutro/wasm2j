package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.ExtHolder;
import io.github.eutro.wasm2j.ext.MetadataState;
import io.github.eutro.wasm2j.ext.TrackedList;

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

    // if we hold on to them forever we can easily OOM, but we don't really
    // care about names except for debugging, so let the JVM clear it up if it has to
    private SoftReference<Map<String, Integer>> varsRef = new SoftReference<>(new HashMap<>());

    public void clearVarNames() {
        varsRef = new SoftReference<>(new HashMap<>());
    }

    public Var newVar(String name) {
        Var var;
        Map<String, Integer> vars = varsRef.get();
        if (vars == null) {
            vars = new HashMap<>();
            varsRef = new SoftReference<>(vars);
        }
        if (vars.containsKey(name)) {
            var = new Var(name, vars.get(name));
            vars.computeIfPresent(name, ($, i) -> i + 1);
        } else {
            var = new Var(name, 0);
            vars.put(name, 0);
        }
        return var;
    }

    {
        attachExt(CommonExts.METADATA_STATE, new MetadataState());
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
}

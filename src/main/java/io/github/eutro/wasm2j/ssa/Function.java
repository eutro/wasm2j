package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.ExtHolder;
import io.github.eutro.wasm2j.ext.TrackedList;

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

    private final Map<String, Set<Var>> vars = new HashMap<>();
    public Var newVar(String name) {
        Var var;
        if (vars.containsKey(name)) {
            Set<Var> varSet = vars.get(name);
            if (name.isEmpty()) {
                name = Integer.toString(varSet.size());
            } else {
                name += "." + varSet.size();
            }
            var = new Var(name);
            varSet.add(var);
        } else {
            var = new Var(name);
            HashSet<Var> set = new HashSet<>();
            set.add(var);
            vars.put(name, set);
        }
        return var;
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

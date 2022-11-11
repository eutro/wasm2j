package io.github.eutro.wasm2j.passes.form;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.passes.meta.ComputeUses;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.util.GraphWalker;
import org.objectweb.asm.Type;

import java.util.*;

public class LinearScan implements InPlaceIRPass<Function> {

    public static final LinearScan INSTANCE = new LinearScan();
    public static final Ext<Integer> IC_EXT = Ext.create(Integer.class);

    @Override
    public void runInPlace(Function func) {
        func.clearVarNames();

        List<BasicBlock> order = GraphWalker.blockWalker(func, true).postOrder().toList();
        Collections.reverse(order);


        Map<Type, Deque<Var>> unusedRegisters = new HashMap<>();
        Map<Var, Var> allocated = new HashMap<>();

        {
            int ic = 0;
            for (BasicBlock block : func.blocks) {
                for (Effect effect : block.getEffects()) {
                    effect.insn().attachExt(IC_EXT, ic++);
                }
                block.getControl().insn.attachExt(IC_EXT, ic++);
            }
        }

        TreeSet<Interval> active = new TreeSet<>(Comparator.comparingInt(o -> o.end));
        {
            int ic = 0;
            for (BasicBlock block : func.blocks) {
                for (Effect effect : block.getEffects()) {

                    { // expire old intervals
                        Iterator<Interval> it = active.iterator();
                        while (it.hasNext()) {
                            Interval last = it.next();
                            if (last.end >= ic) {
                                break;
                            }
                            it.remove();
                            unusedRegisters.get(last.type).add(last.reg);
                        }
                    }

                    for (Var var : effect.getAssignsTo()) {
                        Interval interval = computeLiveInterval(func, var, ic);
                        if (interval == null) continue;
                        active.add(interval);
                        Type type = interval.type = var.getExtOrThrow(JavaExts.TYPE);
                        Deque<Var> varDeque = unusedRegisters.computeIfAbsent(type, $ -> new ArrayDeque<>());
                        interval.reg = varDeque.pollLast();
                        if (interval.reg == null) {
                            interval.reg = func.newVar("reg:" + type.getDescriptor());
                            interval.reg.attachExt(JavaExts.TYPE, type);
                        }
                        allocated.put(var, interval.reg);
                    }
                    ic++;
                }
                ic++;
            }
        }

        {
            for (BasicBlock block : func.blocks) {
                for (Effect effect : block.getEffects()) {
                    replaceVars(effect.getAssignsTo(), allocated);
                    replaceVars(effect.insn(), allocated);
                    effect.insn().removeExt(IC_EXT);
                }
                replaceVars(block.getControl().insn, allocated);
                block.getControl().insn.removeExt(IC_EXT);
            }
        }
    }

    private static void replaceVars(Insn insn, Map<Var, Var> allocated) {
        replaceVars(insn.args, allocated);
    }

    private static void replaceVars(List<Var> vars, Map<Var, Var> allocated) {
        ListIterator<Var> it = vars.listIterator();
        while (it.hasNext()) {
            Var allocatedVar = allocated.get(it.next());
            if (allocatedVar != null) {
                it.set(allocatedVar);
            }
        }
    }

    private static Interval computeLiveInterval(Function func, Var var, int ic) {
        if (var.getExt(CommonExts.STACKIFIED).orElse(false)) return null;

        Set<Insn> uses = var.getExtOrRun(CommonExts.USED_AT, func, ComputeUses.INSTANCE);
        int end = ic; // TODO drop if empty?
        for (Insn use : uses) {
            int useIc = use.getExtOrThrow(IC_EXT);
            if (useIc > end) {
                end = useIc;
            }
        }
        return new Interval(end + 1);
    }

    private static class Interval {
        final int end;
        Var reg;
        Type type;

        private Interval(int end) {
            this.end = end;
        }
    }
}

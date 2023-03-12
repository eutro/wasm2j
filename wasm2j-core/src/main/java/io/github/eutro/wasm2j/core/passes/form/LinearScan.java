package io.github.eutro.wasm2j.core.passes.form;

import io.github.eutro.wasm2j.core.ext.CommonExts;
import io.github.eutro.wasm2j.core.ext.Ext;
import io.github.eutro.wasm2j.core.ext.JavaExts;
import io.github.eutro.wasm2j.core.ext.MetadataState;
import io.github.eutro.wasm2j.core.ops.CommonOps;
import io.github.eutro.wasm2j.core.ops.JavaOps;
import io.github.eutro.wasm2j.core.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.core.ssa.*;
import io.github.eutro.wasm2j.core.util.GraphWalker;
import org.objectweb.asm.Type;

import java.util.*;

/**
 * A pass which allocates registers using a linear scan algorithm.
 * <p>
 * The input IR should have {@link CommonOps#PHI phi} nodes lowered
 * (so not in SSA form). The output IR will also not be in SSA form.
 */
public class LinearScan implements InPlaceIRPass<Function> {
    /**
     * An instance of this pass.
     */
    public static final LinearScan INSTANCE = new LinearScan();

    private static final Ext<Integer> IC_EXT = Ext.create(Integer.class, "IC_EXT");
    private static final Ext<Boolean> DROP = Ext.create(Boolean.class, "DROP");
    private static final Ext<Integer> LAST_LIVE_BLOCK = Ext.create(Integer.class, "LAST_LIVE_BLOCK");

    @Override
    public void runInPlace(Function func) {
        MetadataState ms = func.getExtOrThrow(CommonExts.METADATA_STATE);
        ms.ensureValid(func,
                MetadataState.USES,
                MetadataState.LIVE_DATA);

        func.clearVarNames();

        List<BasicBlock> order = GraphWalker.blockWalker(func, true).postOrder().toList();
        Collections.reverse(order);
        if (order.size() != func.blocks.size()) {
            throw new IllegalStateException();
        }


        Map<Type, Deque<Var>> unusedRegisters = new HashMap<>();
        Map<Var, Var> allocated = new HashMap<>();

        {
            int insnCounter = 0;
            for (BasicBlock block : order) {
                for (Effect effect : block.getEffects()) {
                    effect.insn().attachExt(IC_EXT, insnCounter++);
                }
                block.getControl().insn().attachExt(IC_EXT, insnCounter++);
                CommonExts.LiveData liveData = block.getExtOrThrow(CommonExts.LIVE_DATA);
                for (Var liveOutVar : liveData.liveOut) {
                    // set it to the start of the next block if the variable outlives
                    // this one, this will keep it alive even if this block jumps back
                    liveOutVar.attachExt(LAST_LIVE_BLOCK, insnCounter);
                }
            }
        }

        TreeSet<Interval> active = new TreeSet<>(Comparator.comparingInt(o -> o.end));
        {
            int ic = 0;
            for (BasicBlock block : order) {
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
                        if (allocated.containsKey(var)) {
                            continue;
                        }
                        Interval interval = computeLiveInterval(var, ic);
                        if (interval == null) continue;
                        active.add(interval);
                        Type type = interval.type = var.getExtOrThrow(JavaExts.TYPE);
                        Deque<Var> varDeque = unusedRegisters.computeIfAbsent(type, $ -> new ArrayDeque<>());
                        interval.reg = varDeque.pollLast();
                        if (interval.reg == null) {
                            interval.reg = func.newVarFmt("reg:%s", type);
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
                List<Effect> effects = new ArrayList<>(block.getEffects());
                block.getEffects().clear();
                for (Effect effect : effects) {
                    block.getEffects().add(effect);
                    List<Var> assignsTo = effect.getAssignsTo();
                    replaceVars(assignsTo, allocated);
                    replaceVars(effect.insn(), allocated);
                    for (Var var : assignsTo) {
                        var.removeExt(CommonExts.USED_AT);
                    }
                    if (effect.getNullable(DROP) == Boolean.TRUE) {
                        block.getEffects().add(JavaOps.DROP.insn(assignsTo).assignTo());
                    }
                }
                replaceVars(block.getControl().insn(), allocated);
                block.getControl().insn().removeExt(IC_EXT);
                for (Var liveOutVar : block.getExtOrThrow(CommonExts.LIVE_DATA).liveOut) {
                    liveOutVar.removeExt(LAST_LIVE_BLOCK);
                }
                block.removeExt(CommonExts.LIVE_DATA);
            }
        }

        ms.invalidate(MetadataState.SSA_FORM, MetadataState.USES, MetadataState.LIVE_DATA);
    }

    private static void replaceVars(Insn insn, Map<Var, Var> allocated) {
        replaceVars(insn.args(), allocated);
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

    private static Interval computeLiveInterval(Var var, int ic) {
        if (var.getExt(CommonExts.STACKIFIED).orElse(false)) return null;

        Set<Insn> uses = var.getExtOrThrow(CommonExts.USED_AT);
        if (uses.isEmpty()) {
            Effect effect = var.getExtOrThrow(CommonExts.ASSIGNED_AT);
            if (effect.getAssignsTo().size() == 1) {
                var.attachExt(CommonExts.STACKIFIED, true);
                effect.attachExt(DROP, true);
                return null;
            }
        }
        int end = ic;
        for (Insn use : uses) {
            int useIc = use.getExtOrThrow(IC_EXT);
            if (useIc > end) {
                end = useIc;
            }
        }
        Optional<Integer> llb = var.getExt(LAST_LIVE_BLOCK);
        if (llb.isPresent()) {
            int lastLiveBlockIc = llb.get();
            if (lastLiveBlockIc > end) {
                end = lastLiveBlockIc;
            }
        }
        return new Interval(end);
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

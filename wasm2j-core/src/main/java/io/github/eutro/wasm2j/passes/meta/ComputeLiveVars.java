package io.github.eutro.wasm2j.passes.meta;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.CommonExts.LiveData;
import io.github.eutro.wasm2j.ext.MetadataState;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.ssa.Var;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.ListIterator;
import java.util.Set;

/**
 * Computes the {@link CommonExts#LIVE_DATA} for each block.
 */
public class ComputeLiveVars implements InPlaceIRPass<Function> {
    /**
     * A singleton instance of this pass.
     */
    public static final ComputeLiveVars INSTANCE = new ComputeLiveVars();

    @Override
    public void runInPlace(Function func) {
        MetadataState ms = func.getExtOrThrow(CommonExts.METADATA_STATE);
        ms.ensureValid(func, MetadataState.PREDS);

        for (BasicBlock block : func.blocks) {
            LiveData data = new LiveData();
            block.attachExt(CommonExts.LIVE_DATA, data);
            Set<Var> used = data.gen;
            Set<Var> assigned = data.kill;

            for (Effect effect : block.getEffects()) {
                for (Var arg : effect.insn().args()) {
                    if (!assigned.contains(arg)) used.add(arg);
                }
                assigned.addAll(effect.getAssignsTo());
            }
            for (Var arg : block.getControl().insn().args()) {
                if (!assigned.contains(arg)) used.add(arg);
            }

            data.liveIn.addAll(data.gen);
        }

        Set<BasicBlock> workQueue = new LinkedHashSet<>();
        for (ListIterator<BasicBlock> li = func.blocks.listIterator(func.blocks.size()); li.hasPrevious();) {
            workQueue.add(li.previous());
        }
        while (!workQueue.isEmpty()) {
            Iterator<BasicBlock> iterator = workQueue.iterator();
            BasicBlock next = iterator.next();
            iterator.remove();
            LiveData data = next.getExtOrThrow(CommonExts.LIVE_DATA);
            boolean changed = false;
            for (BasicBlock succ : next.getControl().targets) {
                LiveData succData = succ.getExtOrThrow(CommonExts.LIVE_DATA);
                for (Var varIn : succData.liveIn) {
                    if (data.liveOut.add(varIn)) {
                        if (!data.kill.contains(varIn)) {
                            changed = true;
                            data.liveIn.add(varIn);
                        }
                    }
                }
            }
            if (changed) {
                workQueue.addAll(next.getExtOrThrow(CommonExts.PREDS));
            }
        }
    }
}

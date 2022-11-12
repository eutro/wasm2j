package io.github.eutro.wasm2j.passes.opts;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.MetadataState;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.Function;

import java.util.*;

public class CollapseJumps implements InPlaceIRPass<Function> {
    public static final CollapseJumps INSTANCE = new CollapseJumps();

    @Override
    public void runInPlace(Function func) {
        MetadataState ms = func.getExtOrThrow(CommonExts.METADATA_STATE);
        ms.ensureValid(func, MetadataState.PREDS);

        Map<BasicBlock, BasicBlock> killed = new HashMap<>();
        for (BasicBlock block : func.blocks) {
            if (killed.containsKey(block)) continue;
            while (block.getControl().insn.op.key == CommonOps.BR.key) {
                BasicBlock target = block.getControl().targets.get(0);
                if (target.getExtOrThrow(CommonExts.PREDS).size() != 1) break;
                // if the block has only one predecessor then it won't have phis either

                ArrayList<Effect> insns = new ArrayList<>(target.getEffects());
                target.getEffects().clear();
                block.getEffects().addAll(insns);
                block.setControl(target.getControl());

                killed.put(target, block);
            }
        }

        func.blocks.removeAll(killed.keySet());
        for (BasicBlock block : func.blocks) {
            for (Effect effect : block.getEffects()) {
                if (effect.insn().op.key != CommonOps.PHI) break;
                List<BasicBlock> preds = CommonOps.PHI.cast(effect.insn().op).arg;
                ListIterator<BasicBlock> li = preds.listIterator();
                while (li.hasNext()) {
                    BasicBlock bb = li.next();
                    while (killed.containsKey(bb)) {
                        bb = killed.get(bb);
                    }
                    li.set(bb);
                }
            }
        }

        ms.graphChanged();
    }
}

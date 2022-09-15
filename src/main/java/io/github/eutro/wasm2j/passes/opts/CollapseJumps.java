package io.github.eutro.wasm2j.passes.opts;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.passes.meta.ComputePreds;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.Function;

import java.util.*;

public class CollapseJumps implements InPlaceIRPass<Function> {
    public static final CollapseJumps INSTANCE = new CollapseJumps();

    @Override
    public void runInPlace(Function function) {
        for (BasicBlock block : function.blocks) {
            if (!block.getExt(CommonExts.PREDS).isPresent()) {
                ComputePreds.INSTANCE.runInPlace(function);
                break;
            }
        }

        Map<BasicBlock, BasicBlock> killed = new HashMap<>();
        for (BasicBlock block : function.blocks) {
            if (block.getControl().insn.op.key != CommonOps.BR.key) continue;
            BasicBlock target = block.getControl().targets.get(0);
            if (target.getExtOrThrow(CommonExts.PREDS).size() != 1) continue;

            ArrayList<Effect> insns = new ArrayList<>(target.getEffects());
            target.getEffects().clear();
            block.getEffects().addAll(insns);
            block.setControl(target.getControl());

            killed.put(target, block);
        }

        function.blocks.removeAll(killed.keySet());
        for (BasicBlock block : function.blocks) {
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
    }
}

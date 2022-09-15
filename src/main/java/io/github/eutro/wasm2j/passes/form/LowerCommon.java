package io.github.eutro.wasm2j.passes.form;

import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.Function;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

abstract class LowerCommon implements InPlaceIRPass<Function> {
    @Override
    public void runInPlace(Function func) {
        for (BasicBlock block : func.blocks) {
            BasicBlock sourceBlock = block;
            List<Effect> effects = new ArrayList<>(block.getEffects());
            block.getEffects().clear();
            for (Effect effect : effects) {
                BasicBlock prevBlock = sourceBlock;
                sourceBlock = lowerEffect(func, prevBlock, effect);
                if (sourceBlock == null) {
                    sourceBlock = prevBlock;
                    sourceBlock.addEffect(effect);
                }
            }
            if (sourceBlock != block) {
                for (BasicBlock target : sourceBlock.getControl().targets) {
                    for (Effect effect : target.getEffects()) {
                        if (effect.insn().op.key == CommonOps.PHI) {
                            List<BasicBlock> phiBlocks = CommonOps.PHI.cast(effect.insn().op).arg;
                            ListIterator<BasicBlock> it = phiBlocks.listIterator();
                            while (it.hasNext()) {
                                if (it.next() == block) {
                                    it.set(sourceBlock);
                                    break;
                                }
                            }
                        } else {
                            break;
                        }
                    }
                }
            }
        }
    }

    protected abstract BasicBlock lowerEffect(Function func, BasicBlock sourceBlock, Effect effect);
}

package io.github.eutro.wasm2j.core.passes.form;

import io.github.eutro.wasm2j.core.ext.CommonExts;
import io.github.eutro.wasm2j.core.ext.MetadataState;
import io.github.eutro.wasm2j.core.ops.CommonOps;
import io.github.eutro.wasm2j.core.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.core.ssa.*;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

abstract class LowerCommon implements InPlaceIRPass<Function> {
    @Override
    public void runInPlace(Function func) {
        IRBuilder ib = new IRBuilder(func, null);
        for (BasicBlock block : new ArrayList<>(func.blocks)) {
            ib.setBlock(block);
            Control sourceCtrl = block.getControl();
            block.setControl(null);
            List<Effect> effects = new ArrayList<>(block.getEffects());

            block.getEffects().clear();
            for (Effect effect : effects) {
                if (!lowerEffect(ib, effect)) {
                    ib.insert(effect);
                }
            }
            ib.insertCtrl(sourceCtrl);

            if (block != ib.getBlock()) {
                for (BasicBlock target : ib.getBlock().getControl().targets) {
                    for (Effect effect : target.getEffects()) {
                        if (effect.insn().op.key == CommonOps.PHI) {
                            List<BasicBlock> phiBlocks = CommonOps.PHI.cast(effect.insn().op).arg;
                            ListIterator<BasicBlock> it = phiBlocks.listIterator();
                            while (it.hasNext()) {
                                if (it.next() == block) {
                                    it.set(ib.getBlock());
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

        MetadataState ms = func.getExtOrThrow(CommonExts.METADATA_STATE);
        ms.graphChanged();
    }

    abstract boolean lowerEffect(IRBuilder ib, Effect effect);
}

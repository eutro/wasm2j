package io.github.eutro.wasm2j.passes.meta;

import io.github.eutro.wasm2j.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.passes.InPlaceIrPass;
import io.github.eutro.wasm2j.passes.opts.Opt0;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.ssa.Inliner;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

public class LowerIntrinsics implements InPlaceIrPass<Function> {

    public static final LowerIntrinsics INSTANCE = new LowerIntrinsics();

    @Override
    public void runInPlace(Function func) {
        for (BasicBlock block : func.blocks) {
            BasicBlock sourceBlock = block;
            List<Effect> effects = new ArrayList<>(block.getEffects());
            block.getEffects().clear();
            for (Effect effect : effects) {
                Optional<IntrinsicImpl> intrImpl = JavaOps.INTRINSIC.check(effect.insn().op).map(it -> it.arg);
                if (intrImpl.isPresent()) {
                    IntrinsicImpl intr = intrImpl.get();
                    if (intr.inline) {
                        BasicBlock targetBlock = func.newBb();
                        targetBlock.setControl(sourceBlock.getControl());
                        targetBlock.addEffect(new Inliner(intr.impl, func).inline(
                                effect.insn().args,
                                sourceBlock,
                                targetBlock
                        ).copyFrom(effect));
                        sourceBlock = targetBlock;
                    }
                } else {
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

        Opt0.INSTANCE.runInPlace(func);
    }
}

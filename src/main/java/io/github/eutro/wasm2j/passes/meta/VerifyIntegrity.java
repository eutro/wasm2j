package io.github.eutro.wasm2j.passes.meta;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.Function;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class VerifyIntegrity implements InPlaceIRPass<Function> {
    public static final VerifyIntegrity INSTANCE = new VerifyIntegrity();

    @Override
    public void runInPlace(Function function) {
        Set<BasicBlock> blockSet = new HashSet<>(function.blocks);
        if (blockSet.size() != function.blocks.size()) {
            throw new RuntimeException("function contains duplicate blocks");
        }

        for (BasicBlock block : function.blocks) {
            Iterator<Effect> it = block.getEffects().iterator();
            while (it.hasNext()) {
                Effect effect = it.next();
                if (effect.insn().op.key == CommonOps.PHI) {
                    List<BasicBlock> preds = CommonOps.PHI.cast(effect.insn().op).arg;
                    for (BasicBlock pred : preds) {
                        if (!blockSet.contains(pred)) {
                            throwInvalidReference(block, effect, pred);
                        }
                    }
                } else {
                    break;
                }
            }
            while (it.hasNext()) {
                if (it.next().insn().op.key == CommonOps.PHI) {
                    throw new RuntimeException(String.format(
                            "phi not at block start\n  in block: %s",
                            block));
                }
            }

            for (Effect effect : block.getEffects()) {
                if (effect.getExtOrThrow(CommonExts.OWNING_BLOCK) != block) {
                    throw new IllegalStateException(String.format(
                            "effect not owned by block\n  effect: %s\n  block: %s",
                            effect,
                            block));
                }
            }
            if (block.getControl().getExtOrThrow(CommonExts.OWNING_BLOCK) != block) {
                throw new IllegalStateException(String.format(
                        "control not owned by block\n  control: %s\n  block: %s",
                        block.getControl(),
                        block));
            }

            for (BasicBlock target : block.getControl().targets) {
                if (!blockSet.contains(target)) {
                    throwInvalidReference(block, block.getControl(), target);
                }
            }
        }
    }

    private void throwInvalidReference(BasicBlock block, Object insn, BasicBlock referenced) {
        throw new RuntimeException(String.format(
                "instruction references block not in function;" +
                        "\n  referenced: %s" +
                        "\n  instruction: %s" +
                        "\n  in block: %s",
                referenced.toTargetString(),
                insn,
                block
        ));
    }
}

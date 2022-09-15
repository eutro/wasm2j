package io.github.eutro.wasm2j.passes.meta;

import io.github.eutro.wasm2j.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.Op;
import io.github.eutro.wasm2j.ssa.*;

public class LowerIntrinsics extends LowerCommon {

    public static final LowerIntrinsics INSTANCE = new LowerIntrinsics();

    @Override
    protected BasicBlock lowerEffect(Function func, BasicBlock sourceBlock, Effect effect) {
        Insn insn = effect.insn();
        Op op = insn.op;
        if (op.key == JavaOps.INTRINSIC) {
            IntrinsicImpl intr = JavaOps.INTRINSIC.cast(op).arg;
            if (intr.inline) {
                BasicBlock targetBlock = func.newBb();
                targetBlock.setControl(sourceBlock.getControl());
                targetBlock.addEffect(new Inliner(intr.impl, func).inline(
                        insn.args,
                        sourceBlock,
                        targetBlock
                ).copyFrom(effect));
                return targetBlock;
            }
        }
        return null;
    }
}

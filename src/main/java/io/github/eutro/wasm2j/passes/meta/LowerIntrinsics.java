package io.github.eutro.wasm2j.passes.meta;

import io.github.eutro.wasm2j.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.Op;
import io.github.eutro.wasm2j.ops.OpKey;
import io.github.eutro.wasm2j.passes.InPlaceIrPass;
import io.github.eutro.wasm2j.passes.opts.Opt0;
import io.github.eutro.wasm2j.ssa.*;

import java.util.*;

public class LowerIntrinsics implements InPlaceIrPass<Function> {

    public static final LowerIntrinsics INSTANCE = new LowerIntrinsics();

    @Override
    public void runInPlace(Function func) {
        for (BasicBlock block : func.blocks) {
            BasicBlock sourceBlock = block;
            List<Effect> effects = new ArrayList<>(block.getEffects());
            block.getEffects().clear();
            for (Effect effect : effects) {
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
                        sourceBlock = targetBlock;
                    }
                } else if (op.key == JavaOps.SELECT || op.key == JavaOps.BOOL_SELECT) {
                    boolean isBool;
                    JavaOps.JumpType jt;
                    if (op.key == JavaOps.SELECT) {
                        jt = JavaOps.SELECT.cast(op).arg;
                        isBool = false;
                    } else {
                        jt = JavaOps.BOOL_SELECT.cast(op).arg;
                        isBool = true;
                    }

                    BasicBlock targetBlock = func.newBb();
                    targetBlock.setControl(sourceBlock.getControl());

                    BasicBlock ifT = func.newBb();
                    BasicBlock ifF = func.newBb();
                    sourceBlock.setControl(JavaOps.BR_COND.create(jt)
                            .insn(isBool ? insn.args : insn.args.subList(2, insn.args.size()))
                            .jumpsTo(ifT, ifF));
                    ifT.setControl(Control.br(targetBlock));
                    ifF.setControl(Control.br(targetBlock));

                    Var ifTV = func.newVar("ift");
                    ifT.addEffect((isBool
                            ? CommonOps.CONST.create(1).insn()
                            : CommonOps.IDENTITY.insn(insn.args.get(0)))
                            .assignTo(ifTV));
                    Var ifFV = func.newVar("iff");
                    ifF.addEffect((isBool
                            ? CommonOps.CONST.create(0).insn()
                            : CommonOps.IDENTITY.insn(insn.args.get(1)))
                            .assignTo(ifFV));
                    targetBlock.addEffect(CommonOps.PHI
                            .create(Arrays.asList(ifF, ifT))
                            .insn(ifFV, ifTV)
                            .copyFrom(effect));

                    sourceBlock = targetBlock;
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
    }
}

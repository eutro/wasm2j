package io.github.eutro.wasm2j.passes.opts;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.MetadataState;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.Op;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Control;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.Function;

import java.util.Iterator;
import java.util.List;

public class FindBoolSelects implements InPlaceIRPass<Function> {

    public static final FindBoolSelects INSTANCE = new FindBoolSelects();

    @Override
    public void runInPlace(Function func) {
        MetadataState ms = func.getExtOrThrow(CommonExts.METADATA_STATE);
        ms.ensureValid(func, MetadataState.PREDS);

        boolean changed = false;
        for (BasicBlock block : func.blocks) {
            if (block.getEffects().isEmpty()) continue;
            Iterator<Effect> iter = block.getEffects().iterator();
            Effect phi = iter.next();
            Op op = phi.insn().op;
            if (op.key != CommonOps.PHI) continue;
            if (iter.hasNext() && iter.next().insn().op.key == CommonOps.PHI) continue;
            List<BasicBlock> preds = CommonOps.PHI.cast(op).arg;
            if (preds.size() != 2) continue;
            List<BasicBlock> bPred = preds.get(0).getExtOrThrow(CommonExts.PREDS);
            if (bPred.size() != 1) continue;
            List<BasicBlock> aPred = preds.get(1).getExtOrThrow(CommonExts.PREDS);
            if (aPred.size() != 1) continue;
            if (bPred.get(0) != aPred.get(0)) continue;
            BasicBlock pred = aPred.get(0);

            Control jump = pred.getControl();
            if (jump.insn().op.key != JavaOps.BR_COND) continue;

            JavaOps.JumpType jTy = JavaOps.BR_COND.cast(jump.insn().op).arg;
            List<BasicBlock> jumpTargets = jump.targets;
            if (jumpTargets.size() != 2
                    || jumpTargets.get(0).getEffects().size() != 1
                    || jumpTargets.get(1).getEffects().size() != 1) continue;

            if (jumpTargets.get(0).getControl().insn().op.key != CommonOps.BR.key
                    || jumpTargets.get(1).getControl().insn().op.key != CommonOps.BR.key) continue;

            Effect ifFInsn = jumpTargets.get(0).getEffects().get(0);
            Effect ifTInsn = jumpTargets.get(1).getEffects().get(0);
            if (!(ifFInsn.insn().op.key == CommonOps.CONST
                    && ((Object) 0).equals(CommonOps.CONST.cast(ifFInsn.insn().op).arg))) continue;
            if (!(ifTInsn.insn().op.key == CommonOps.CONST
                    && ((Object) 1).equals(CommonOps.CONST.cast(ifTInsn.insn().op).arg))) continue;

            if (!phi.insn().args().contains(ifFInsn.getAssignsTo().get(0))
                    || !phi.insn().args().contains(ifTInsn.getAssignsTo().get(0))) continue;

            phi.setInsn(JavaOps.BOOL_SELECT.create(jTy).copyFrom(jump.insn()));
            pred.setControl(Control.br(block));
            changed = true;
        }

        if (changed) {
            ms.graphChanged();
        }
    }
}

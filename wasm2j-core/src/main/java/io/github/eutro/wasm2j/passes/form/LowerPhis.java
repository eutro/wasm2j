package io.github.eutro.wasm2j.passes.form;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.MetadataState;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.ssa.Var;

import java.util.Iterator;
import java.util.List;

public class LowerPhis implements InPlaceIRPass<Function> {
    public static final LowerPhis INSTANCE = new LowerPhis();

    @Override
    public void runInPlace(Function func) {
        for (BasicBlock block : func.blocks) {
            Iterator<Effect> iter = block.getEffects().iterator();
            while (iter.hasNext()) {
                Effect phi = iter.next();
                if (phi.insn().op.key != CommonOps.PHI) {
                    break;
                }
                List<BasicBlock> preds = CommonOps.PHI.cast(phi.insn().op).arg;
                List<Var> vars = phi.insn().args;
                assert preds.size() == vars.size();
                Iterator<BasicBlock> bbIt = preds.iterator();
                Iterator<Var> varIt = vars.iterator();
                while (bbIt.hasNext()) {
                    BasicBlock pred = bbIt.next();
                    Var var = varIt.next();
                    pred.getEffects().add(CommonOps.IDENTITY.insn(var).copyFrom(phi));
                }

                for (Var var : phi.getAssignsTo()) {
                    var.attachExt(CommonExts.IS_PHI, true);
                }

                iter.remove();
            }
        }

        MetadataState ms = func.getExtOrThrow(CommonExts.METADATA_STATE);
        ms.invalidate(MetadataState.SSA_FORM);
        ms.graphChanged();
    }
}

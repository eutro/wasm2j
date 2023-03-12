package io.github.eutro.wasm2j.core.passes.form;

import io.github.eutro.wasm2j.core.ops.CommonOps;
import io.github.eutro.wasm2j.core.ssa.BasicBlock;
import io.github.eutro.wasm2j.core.ssa.Effect;
import io.github.eutro.wasm2j.core.ssa.Function;
import io.github.eutro.wasm2j.core.ssa.Var;
import io.github.eutro.wasm2j.core.ext.CommonExts;
import io.github.eutro.wasm2j.core.ext.MetadataState;
import io.github.eutro.wasm2j.core.passes.InPlaceIRPass;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * A pass which lowers {@link CommonOps#PHI phi} nodes into
 * assignments targeting the same variables.
 * <p>
 * The input should be in SSA form, but the output will not.
 * This is usually the first step in leaving SSA form.
 */
public class LowerPhis implements InPlaceIRPass<Function> {
    /**
     * An instance of this pass.
     */
    public static final LowerPhis INSTANCE = new LowerPhis();

    @Override
    public void runInPlace(Function func) {
        for (BasicBlock block : func.blocks) {
            ListIterator<Effect> iter = block.getEffects().listIterator();
            while (iter.hasNext()) {
                Effect phi = iter.next();
                if (phi.insn().op.key != CommonOps.PHI) {
                    break;
                }
                iter.remove();

                List<BasicBlock> preds = CommonOps.PHI.cast(phi.insn().op).arg;
                List<Var> vars = phi.insn().args();
                assert preds.size() == vars.size();

                for (Var var : phi.getAssignsTo()) {
                    var.attachExt(CommonExts.IS_PHI, true);
                }

                Iterator<BasicBlock> bbIt = preds.iterator();
                Iterator<Var> varIt = vars.iterator();
                while (bbIt.hasNext()) {
                    BasicBlock pred = bbIt.next();
                    Var var = varIt.next();
                    pred.getEffects().add(CommonOps.IDENTITY.insn(var).copyFrom(phi));
                    if (pred == block) {
                        iter = block.getEffects().listIterator(iter.nextIndex());
                    }
                }
            }
        }

        MetadataState ms = func.getExtOrThrow(CommonExts.METADATA_STATE);
        ms.invalidate(MetadataState.SSA_FORM);
        ms.graphChanged();
    }
}

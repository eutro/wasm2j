package io.github.eutro.wasm2j.passes.meta;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.passes.InPlaceIrPass;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.ssa.Var;

import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class LowerPhis implements InPlaceIrPass<Function> {
    public static final LowerPhis INSTANCE = new LowerPhis();

    @Override
    public void runInPlace(Function func) {
        for (BasicBlock block : func.blocks) {
            for (Effect phi : block.getEffects()) {
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
                Var phiVar = phi.getAssignsTo().get(0);
                phiVar.attachExt(CommonExts.ASSIGNED_AT, phi);
                ListIterator<Var> it = phi.insn().args.listIterator();
                while (it.hasNext()) {
                    it.next();
                    it.set(phiVar);
                }
                phi.attachExt(CommonExts.PHI_LOWERED, true);
            }
        }
    }
}

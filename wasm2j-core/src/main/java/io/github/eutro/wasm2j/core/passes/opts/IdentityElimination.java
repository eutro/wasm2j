package io.github.eutro.wasm2j.core.passes.opts;

import io.github.eutro.wasm2j.core.ops.CommonOps;
import io.github.eutro.wasm2j.core.ssa.Var;
import io.github.eutro.wasm2j.core.ext.CommonExts;
import io.github.eutro.wasm2j.core.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.core.ssa.Insn;

import java.util.ListIterator;

/**
 * A pass which contracts {@link CommonOps#IDENTITY} instructions.
 */
public class IdentityElimination implements InPlaceIRPass<Insn> {
    /**
     * An instance of this pass.
     */
    public static final IdentityElimination INSTANCE = new IdentityElimination();

    @Override
    public void runInPlace(Insn insn) {
        ListIterator<Var> iter = insn.args().listIterator();
        while (iter.hasNext()) {
            Var arg = iter.next();
            arg.getExt(CommonExts.ASSIGNED_AT).ifPresent(assigned -> {
                if (assigned.insn().op.key != CommonOps.IDENTITY.key) return;
                if (assigned.getAssignsTo().size() != 1) return;
                if (assigned.insn().args().size() != 1) return;
                iter.set(assigned.insn().args().get(0));
            });
        }
    }
}

package io.github.eutro.wasm2j.passes.opts;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.MetadataState;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.UnaryOpKey;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.*;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Optional;

/**
 * A pass which merges {@link JavaOps#BOOL_SELECT} instructions
 * with {@link JavaOps#BR_COND} and {@link JavaOps#SELECT} instructions.
 * <p>
 * These cases are common because of the way Java jump instructions differ
 * from WebAssembly jumps. The former includes the test in the jump,
 * whereas the latter has a separate instruction for performing the test,
 * followed by the jump
 */
public class MergeConds implements InPlaceIRPass<Function> {
    /**
     * An instance of this pass.
     */
    public static final MergeConds INSTANCE = new MergeConds();

    interface Cont {
        void run(Insn insn, JavaOps.JumpType boolTy);
    }

    private void checkSelect(Var arg, Cont k) {
        Optional<Effect> assignedAt = arg.getExt(CommonExts.ASSIGNED_AT);
        if (!assignedAt.isPresent()) return;
        Effect assigned = assignedAt.get();
        if (assigned.insn().op.key != JavaOps.BOOL_SELECT) return;
        if (arg.getExtOrThrow(CommonExts.USED_AT).size() > 1) return;
        JavaOps.JumpType boolTy = JavaOps.BOOL_SELECT.cast(assigned.insn().op).arg;
        k.run(assigned.insn(), boolTy);
    }

    @Override
    public void runInPlace(Function func) {
        MetadataState ms = func.getExtOrThrow(CommonExts.METADATA_STATE);
        ms.ensureValid(func, MetadataState.USES);

        for (BasicBlock basicBlock : func.blocks) {
            ListIterator<Effect> li = basicBlock.getEffects().listIterator(basicBlock.getEffects().size());
            while (li.hasPrevious()) {
                Effect prev = li.previous();
                Insn insn = prev.insn();
                if (insn.op.key != JavaOps.SELECT || insn.args().size() != 3) continue;
                Var arg = insn.args().get(2);
                checkSelect(arg, (aInsn, boolTy) -> {
                    JavaOps.JumpType thisTy = JavaOps.SELECT.cast(insn.op).arg;
                    JavaOps.JumpType combined = thisTy.combine(boolTy);
                    if (combined == null) return;
                    List<Var> args = new ArrayList<>(insn.args().subList(0, 2));
                    args.addAll(aInsn.args());
                    prev.setInsn(JavaOps.SELECT.create(combined).insn(args));
                });
            }

            Control jump = basicBlock.getControl();
            Insn insn = jump.insn();
            if (insn.op.key != JavaOps.BR_COND || insn.args().size() != 1) continue;
            Var arg = insn.args().get(0);
            checkSelect(arg, (aInsn, boolTy) -> {
                UnaryOpKey<JavaOps.JumpType>.UnaryOp jumpKey = JavaOps.BR_COND.cast(insn.op);
                JavaOps.JumpType combined = jumpKey.arg.combine(boolTy);
                if (combined == null) return;
                jump.setInsn(JavaOps.BR_COND.create(combined).copyFrom(aInsn));
            });
        }

        ms.varsChanged();
    }
}

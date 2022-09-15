package io.github.eutro.wasm2j.passes.opts;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.UnaryOpKey;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.*;

import java.util.Optional;

public class MergeConds implements InPlaceIRPass<BasicBlock> {

    public static final MergeConds INSTANCE = new MergeConds();

    @Override
    public void runInPlace(BasicBlock basicBlock) {
        Control jump = basicBlock.getControl();
        Insn insn = jump.insn;
        if (insn.op.key != JavaOps.BR_COND || insn.args.size() == 1) return;
        Var arg = insn.args.get(0);
        Optional<Effect> assignedAt = arg.getExt(CommonExts.ASSIGNED_AT);
        if (!assignedAt.isPresent()) return;
        Effect assigned = assignedAt.get();
        if (assigned.insn().op.key != JavaOps.BOOL_SELECT) return;
        JavaOps.JumpType boolTy = JavaOps.BOOL_SELECT.cast(assigned.insn().op).arg;
        UnaryOpKey<JavaOps.JumpType>.UnaryOp jumpKey = JavaOps.BR_COND.cast(insn.op);
        JavaOps.JumpType combined = jumpKey.arg.combine(boolTy);
        if (combined == null) return;
        jump.insn = JavaOps.BR_COND.create(combined).copyFrom(assigned.insn());
    }
}

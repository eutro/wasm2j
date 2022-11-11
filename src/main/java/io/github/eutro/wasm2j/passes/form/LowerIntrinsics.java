package io.github.eutro.wasm2j.passes.form;

import io.github.eutro.wasm2j.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.Op;
import io.github.eutro.wasm2j.ssa.*;

import java.util.Objects;

public class LowerIntrinsics extends LowerCommon {

    public static final LowerIntrinsics INSTANCE = new LowerIntrinsics();

    @Override
    protected boolean lowerEffect(IRBuilder ib, Effect effect) {
        Insn insn = effect.insn();
        Op op = insn.op;
        if (op.key == JavaOps.INTRINSIC) {
            IntrinsicImpl intr = JavaOps.INTRINSIC.cast(op).arg;
            if (intr.inline) {
                ib.insert(new Inliner(ib)
                        .inline(Objects.requireNonNull(intr.impl), insn.args)
                        .copyFrom(effect));
                return true;
            }
        }
        return false;
    }
}

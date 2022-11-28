package io.github.eutro.wasm2j.passes.form;

import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.Op;
import io.github.eutro.wasm2j.ssa.*;

import java.util.Arrays;

public class LowerSelects extends LowerCommon {

    public static final LowerSelects INSTANCE = new LowerSelects();

    @Override
    protected boolean lowerEffect(IRBuilder ib, Effect effect) {
        Insn insn = effect.insn();
        Op op = insn.op;
        if (op.key == JavaOps.SELECT || op.key == JavaOps.BOOL_SELECT) {
            boolean isBool;
            JavaOps.JumpType jt;
            if (op.key == JavaOps.SELECT) {
                jt = JavaOps.SELECT.cast(op).arg;
                isBool = false;
            } else {
                jt = JavaOps.BOOL_SELECT.cast(op).arg;
                isBool = true;
            }

            BasicBlock targetBlock = ib.func.newBb();

            BasicBlock ifT = ib.func.newBb();
            BasicBlock ifF = ib.func.newBb();
            ib.insertCtrl(JavaOps.BR_COND.create(jt)
                    .insn(isBool ? insn.args : insn.args.subList(0, insn.args.size() - 2))
                    .jumpsTo(ifF, ifT));
            ifT.setControl(Control.br(targetBlock));
            ifF.setControl(Control.br(targetBlock));

            ib.setBlock(ifT);
            Var ifTV = ib.insert((isBool
                            ? CommonOps.constant(1)
                            : CommonOps.IDENTITY.insn(insn.args.get(1))),
                    "ift");

            ib.setBlock(ifF);
            Var ifFV = ib.insert((isBool
                            ? CommonOps.constant(0)
                            : CommonOps.IDENTITY.insn(insn.args.get(2))),
                    "iff");

            ib.setBlock(targetBlock);
            ib.insert(CommonOps.PHI
                    .create(Arrays.asList(ifF, ifT))
                    .insn(ifFV, ifTV)
                    .copyFrom(effect));

            return true;
        }
        return false;
    }
}

package io.github.eutro.wasm2j.passes.form;

import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.Op;
import io.github.eutro.wasm2j.ssa.*;

import java.util.Arrays;

public class LowerSelects extends LowerCommon {

    public static final LowerSelects INSTANCE = new LowerSelects();

    @Override
    protected BasicBlock lowerEffect(Function func, BasicBlock sourceBlock, Effect effect) {
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

            return targetBlock;
        }
        return null;
    }
}

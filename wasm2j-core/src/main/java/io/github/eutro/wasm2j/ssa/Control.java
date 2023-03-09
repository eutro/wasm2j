package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.ExtHolder;
import io.github.eutro.wasm2j.ops.CommonOps;

import java.util.List;

public final class Control extends ExtHolder {
    private Insn insn;
    public List<BasicBlock> targets;

    public Control(Insn insn, List<BasicBlock> targets) {
        this.setInsn(insn);
        this.targets = targets;
    }

    public static Control br(BasicBlock target) {
        return CommonOps.BR.insn().jumpsTo(target);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(insn());
        if (!targets.isEmpty()) {
            sb.append(" ->");
            for (BasicBlock target : targets) {
                sb.append(' ').append(target.toTargetString());
            }
        }
        return sb.toString();
    }

    public Insn insn() {
        return insn;
    }

    public void setInsn(Insn insn) {
        insn.attachExt(CommonExts.OWNING_CONTROL, this);
        this.insn = insn;
    }
}

package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ext.ExtHolder;
import io.github.eutro.wasm2j.ops.CommonOps;
import org.jetbrains.annotations.Nullable;

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

    // exts
    private BasicBlock owner = null;

    @SuppressWarnings("unchecked")
    @Override
    public <T> @Nullable T getNullable(Ext<T> ext) {
        if (ext == CommonExts.OWNING_BLOCK) {
            return (T) owner;
        }
        return super.getNullable(ext);
    }

    @Override
    public <T> void attachExt(Ext<T> ext, T value) {
        if (ext == CommonExts.OWNING_BLOCK) {
            owner = (BasicBlock) value;
            return;
        }
        super.attachExt(ext, value);
    }

    @Override
    public <T> void removeExt(Ext<T> ext) {
        if (ext == CommonExts.OWNING_BLOCK) {
            owner = null;
            return;
        }
        super.removeExt(ext);
    }
}

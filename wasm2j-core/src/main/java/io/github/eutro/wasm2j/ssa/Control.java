package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ext.ExtHolder;
import io.github.eutro.wasm2j.ops.CommonOps;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A control instruction, encapsulating a raw {@link Insn instruction}
 * and the jump targets.
 */
public final class Control extends ExtHolder {
    private Insn insn;
    /**
     * The jump targets of this instruction. The semantics of the order depend on the instruction.
     */
    public final List<BasicBlock> targets;

    Control(Insn insn, List<BasicBlock> targets) {
        this.setInsn(insn);
        this.targets = targets;
    }

    /**
     * Construct an unconditional jump to a block.
     *
     * @param target The jump target.
     * @return The jump instruction.
     */
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

    /**
     * Get the {@link Insn underlying instruction} of this control instruction.
     *
     * @return The instruction.
     */
    public Insn insn() {
        return insn;
    }

    /**
     * Set the {@link Insn underlying instruction} of this control instruction.
     *
     * @param insn The instruction.
     */
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

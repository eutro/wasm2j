package io.github.eutro.wasm2j.passes.opts;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.util.F;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class PropagateConstants implements InPlaceIRPass<Function> {
    public static final PropagateConstants INSTANCE = new PropagateConstants();

    @Override
    public void runInPlace(Function function) {
        Set<Insn> seen = new HashSet<>();
        Deque<Frame> stack = new ArrayDeque<>();
        for (BasicBlock block : function.blocks) {
            for (Effect effect : block.getEffects()) {
                push(seen, stack, effect.insn());
            }
            push(seen, stack, block.getControl().insn());
        }
    }

    static class Frame {
        Insn insn;
        boolean justPushed = true;

        Frame(Insn insn) {
            this.insn = insn;
        }
    }

    private void push(Set<Insn> seen, Deque<Frame> stack, Insn insn) {
        if (!seen.add(insn)) return;
        stack.push(new Frame(insn));
        while (!stack.isEmpty()) {
            Frame top = stack.pop();
            if (top.justPushed) {
                stack.push(top);
                for (Var arg : top.insn.args()) {
                    Insn argInsn = arg.getExtOrThrow(CommonExts.ASSIGNED_AT).insn();
                    if (seen.add(argInsn)) {
                        stack.push(new Frame(argInsn));
                    }
                }
                top.justPushed = false;
            } else {
                if (top.insn.op.key != CommonOps.CONST) {
                    F<Insn, Insn> propagate = top.insn.op.getNullable(CommonExts.CONSTANT_PROPAGATOR);
                    if (propagate == null) continue;
                    Insn newInsn = propagate.apply(top.insn);
                    if (newInsn != top.insn) {
                        seen.add(newInsn);
                    }
                    Effect owner = top.insn.getNullable(CommonExts.OWNING_EFFECT);
                    if (owner != null) {
                        owner.setInsn(newInsn);
                    } else {
                        top.insn.getExtOrThrow(CommonExts.OWNING_CONTROL)
                                .setInsn(newInsn);
                    }
                    top.insn = newInsn;
                }
                if (top.insn.op.key == CommonOps.CONST) {
                    Effect owner = top.insn.getNullable(CommonExts.OWNING_EFFECT);
                    if (owner != null) {
                        for (Var var : owner.getAssignsTo()) {
                            var.attachExt(CommonExts.CONSTANT_VALUE,
                                    CommonExts.fillNull(CommonOps.CONST.cast(top.insn.op).arg));
                        }
                    }
                }
            }
        }
    }
}

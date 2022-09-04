package io.github.eutro.wasm2j.passes.opts;

import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.passes.InPlaceIrPass;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Control;
import io.github.eutro.wasm2j.ssa.Function;

import java.util.*;

public class Opt0 implements InPlaceIrPass<Function> {
    public static final Opt0 INSTANCE = new Opt0();

    @Override
    public void runInPlace(Function function) {
        opt0(function);
    }

    private static void opt0(Function func) {
        // primitive optimisations that can be done even before we are in SSA,
        // and which reduce the size of the graph significantly
        // also sorts the list into pre-walk order
        for (BasicBlock block : func.blocks) {
            collapseJumpTargets(block);
        }
        eliminateDeadBlocks(func);
    }

    private static void eliminateDeadBlocks(Function func) {
        Set<BasicBlock> alive = new LinkedHashSet<>();
        List<BasicBlock> queue = new ArrayList<>();
        BasicBlock root = func.blocks.get(0);
        queue.add(root);
        alive.add(root);
        while (!queue.isEmpty()) {
            BasicBlock next = queue.remove(queue.size() - 1);
            for (BasicBlock target : next.getControl().targets) {
                if (alive.add(target)) queue.add(target);
            }
        }
        func.blocks.clear();
        func.blocks.addAll(alive);
    }

    private static void collapseJumpTargets(BasicBlock bb) {
        List<BasicBlock> targets = bb.getControl().targets;
        ListIterator<BasicBlock> it = targets.listIterator();
        while (it.hasNext()) {
            it.set(maybeCollapseTarget(it.next()));
        }
    }

    private static BasicBlock maybeCollapseTarget(BasicBlock target) {
        if (target.getEffects().isEmpty()) {
            Control targetControl = target.getControl();
            if (targetControl.insn.op == CommonOps.BR) {
                if (targetControl.targets.size() == 1) {
                    BasicBlock targetOut = targetControl.targets.get(0);
                    if (targetOut == target) return target;
                    BasicBlock newTarget = maybeCollapseTarget(targetOut);
                    targetControl.targets.set(0, newTarget);
                    return newTarget;
                }
            }
        }
        return target;
    }
}

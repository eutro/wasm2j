package io.github.eutro.wasm2j.passes.opts;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.ssa.Var;

import java.util.*;

public class EliminateDeadVars implements InPlaceIRPass<Function> {
    public static final EliminateDeadVars INSTANCE = new EliminateDeadVars();

    @Override
    public void runInPlace(Function function) {
        Map<Var, Integer> usageCount = new HashMap<>();
        for (BasicBlock block : function.blocks) {
            for (Effect effect : block.getEffects()) {
                for (Var arg : effect.insn().args) {
                    usageCount.compute(arg, (var, integer) -> integer == null ? 1 : integer + 1);
                }
                for (Var var : effect.getAssignsTo()) {
                    usageCount.putIfAbsent(var, 0);
                }
            }
            for (Var arg : block.getControl().insn.args) {
                usageCount.compute(arg, (var, integer) -> integer == null ? 1 : integer + 1);
            }
        }

        Set<Effect> deadEffects = new HashSet<>();
        Set<Var> dead = new HashSet<>();
        List<Var> stack = new ArrayList<>();
        usageCount.forEach((var, integer) -> {
            if (integer == 0) {
                stack.add(var);
                dead.add(var);
            }
        });

        while (!stack.isEmpty()) {
            Var deadVar = stack.remove(stack.size() - 1);
            deadVar.getExt(CommonExts.ASSIGNED_AT).ifPresent(effect -> {
                if (!effect.getExt(CommonExts.IS_PURE).orElse(false)) return;
                for (Var var : effect.getAssignsTo()) {
                    if (!dead.contains(var)) return;
                }
                deadEffects.add(effect);
                for (Var arg : effect.insn().args) {
                    usageCount.compute(arg, (var, integer) -> {
                        int n = integer == null ? 0 : integer - 1;
                        if (n == 0) {
                            if (dead.add(var)) {
                                stack.add(var);
                            }
                        }
                        return n;
                    });
                }
            });
        }

        for (BasicBlock block : function.blocks) {
            block.getEffects().removeAll(deadEffects);
        }

        function.getExtOrThrow(CommonExts.METADATA_STATE)
                .graphChanged();
    }
}

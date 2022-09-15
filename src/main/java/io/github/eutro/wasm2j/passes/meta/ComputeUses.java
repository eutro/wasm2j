package io.github.eutro.wasm2j.passes.meta;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class ComputeUses implements InPlaceIRPass<Function> {
    public static final ComputeUses INSTANCE = new ComputeUses();

    @Override
    public void runInPlace(Function func) {
        for (BasicBlock block : func.blocks) {
            for (Effect effect : block.getEffects()) {
                for (Var arg : effect.insn().args) {
                    getOrCreateUses(arg).add(effect.insn());
                }
                for (Var var : effect.getAssignsTo()) {
                    getOrCreateUses(var);
                }
            }
            Control ctrl = block.getControl();
            for (Var arg : ctrl.insn.args) {
                getOrCreateUses(arg).add(ctrl.insn);
            }
        }
    }

    @NotNull
    private static Set<Insn> getOrCreateUses(Var arg) {
        return arg.getExt(CommonExts.USED_AT).orElseGet(() -> {
            HashSet<Insn> set = new HashSet<>();
            arg.attachExt(CommonExts.USED_AT, set);
            return set;
        });
    }
}

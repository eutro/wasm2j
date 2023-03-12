package io.github.eutro.wasm2j.core.passes.meta;

import io.github.eutro.wasm2j.core.ext.CommonExts;
import io.github.eutro.wasm2j.core.ext.MetadataState;
import io.github.eutro.wasm2j.core.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.core.ssa.*;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * Compute the {@link CommonExts#USED_AT uses} of every {@link Var} in a function.
 */
public class ComputeUses implements InPlaceIRPass<Function> {
    /**
     * A singleton instance of this pass.
     */
    public static final ComputeUses INSTANCE = new ComputeUses();

    @Override
    public void runInPlace(Function func) {
        for (BasicBlock block : func.blocks) {
            for (Effect effect : block.getEffects()) {
                for (Var arg : effect.insn().args()) {
                    clearUses(arg);
                }
                for (Var var : effect.getAssignsTo()) {
                    clearUses(var);
                }
            }
            Control ctrl = block.getControl();
            for (Var arg : ctrl.insn().args()) {
                clearUses(arg);
            }
        }
        for (BasicBlock block : func.blocks) {
            for (Effect effect : block.getEffects()) {
                for (Var arg : effect.insn().args()) {
                    getOrCreateUses(arg).add(effect.insn());
                }
                for (Var var : effect.getAssignsTo()) {
                    getOrCreateUses(var);
                }
            }
            Control ctrl = block.getControl();
            for (Var arg : ctrl.insn().args()) {
                getOrCreateUses(arg).add(ctrl.insn());
            }
        }

        MetadataState ms = func.getExtOrThrow(CommonExts.METADATA_STATE);
        ms.validate(MetadataState.USES);
    }

    private void clearUses(Var arg) {
        arg.removeExt(CommonExts.USED_AT);
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

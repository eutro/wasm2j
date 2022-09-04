package io.github.eutro.wasm2j.passes.meta;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.passes.InPlaceIrPass;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Function;

import java.util.HashSet;
import java.util.List;

public class ComputeDomFrontier implements InPlaceIrPass<Function> {
    public static final ComputeDomFrontier INSTANCE = new ComputeDomFrontier();

    @Override
    public void runInPlace(Function function) {
        computeDomFrontier(function);
    }

    private static void computeDomFrontier(Function func) {
        for (BasicBlock block : func.blocks) {
            if (!block.getExt(CommonExts.PREDS).isPresent() || !block.getExt(CommonExts.IDOM).isPresent()) {
                ComputeDoms.INSTANCE.runInPlace(func);
                break;
            }
        }

        // Cooper, Keith D.; Harvey, Timothy J.; Kennedy, Ken (2001). "A Simple, Fast Dominance Algorithm"
        for (BasicBlock block : func.blocks) {
            block.attachExt(CommonExts.DOM_FRONTIER, new HashSet<>());
        }
        for (BasicBlock block : func.blocks) {
            List<BasicBlock> preds = block.getExtOrThrow(CommonExts.PREDS);
            if (preds.size() >= 2) {
                for (BasicBlock pred : preds) {
                    BasicBlock runner = pred;
                    while (runner != block.getExtOrThrow(CommonExts.IDOM)) {
                        runner.getExtOrThrow(CommonExts.DOM_FRONTIER).add(block);
                        runner = runner.getExtOrThrow(CommonExts.IDOM);
                    }
                }
            }
        }
    }
}
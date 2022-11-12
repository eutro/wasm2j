package io.github.eutro.wasm2j.passes.meta;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.MetadataState;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Function;

import java.util.HashSet;
import java.util.List;

public class ComputeDomFrontier implements InPlaceIRPass<Function> {
    public static final ComputeDomFrontier INSTANCE = new ComputeDomFrontier();

    @Override
    public void runInPlace(Function function) {
        computeDomFrontier(function);
    }

    private static void computeDomFrontier(Function func) {
        MetadataState ms = func.getExtOrThrow(CommonExts.METADATA_STATE);
        ms.ensureValid(func, MetadataState.DOMS, MetadataState.PREDS);

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

        ms.validate(MetadataState.DOM_FRONTIER);
    }
}

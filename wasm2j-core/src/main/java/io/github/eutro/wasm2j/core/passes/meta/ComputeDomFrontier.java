package io.github.eutro.wasm2j.core.passes.meta;

import io.github.eutro.wasm2j.core.ssa.BasicBlock;
import io.github.eutro.wasm2j.core.ssa.Function;
import io.github.eutro.wasm2j.core.ext.CommonExts;
import io.github.eutro.wasm2j.core.ext.MetadataState;
import io.github.eutro.wasm2j.core.passes.InPlaceIRPass;

import java.util.HashSet;
import java.util.List;

/**
 * Computes {@link CommonExts#DOM_FRONTIER} for each block.
 */
public class ComputeDomFrontier implements InPlaceIRPass<Function> {
    /**
     * A singleton instance of this pass.
     */
    public static final ComputeDomFrontier INSTANCE = new ComputeDomFrontier();

    @Override
    public void runInPlace(Function func) {
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

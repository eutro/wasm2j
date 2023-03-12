package io.github.eutro.wasm2j.passes.opts;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.util.GraphWalker;

import java.util.HashSet;

/**
 * An optimisation pass that removes any blocks unreachable from the root block.
 */
public class EliminateDeadBlocks implements InPlaceIRPass<Function> {
    /**
     * An instance of this pass.
     */
    public static final EliminateDeadBlocks INSTANCE = new EliminateDeadBlocks();

    @Override
    public void runInPlace(Function function) {
        function.blocks.retainAll(new HashSet<>(
                GraphWalker.blockWalker(function)
                        .preOrder()
                        .toList()));
        function.getExtOrThrow(CommonExts.METADATA_STATE)
                .graphChanged();
    }
}

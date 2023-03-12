package io.github.eutro.wasm2j.core.passes.opts;

import io.github.eutro.wasm2j.core.ssa.Function;
import io.github.eutro.wasm2j.core.ext.CommonExts;
import io.github.eutro.wasm2j.core.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.core.util.GraphWalker;

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

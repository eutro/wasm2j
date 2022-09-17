package io.github.eutro.wasm2j.passes.opts;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.util.GraphWalker;

import java.util.HashSet;

public class EliminateDeadBlocks implements InPlaceIRPass<Function> {

    public static final EliminateDeadBlocks INSTANCE = new EliminateDeadBlocks();

    @Override
    public void runInPlace(Function function) {
        function.blocks.retainAll(new HashSet<>(
                GraphWalker.blockWalker(function)
                        .preOrder()
                        .toList()));
        for (BasicBlock block : function.blocks) {
            block.removeExt(CommonExts.PREDS);
        }
    }
}

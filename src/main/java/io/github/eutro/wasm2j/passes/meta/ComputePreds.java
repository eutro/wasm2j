package io.github.eutro.wasm2j.passes.meta;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.MetadataState;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Function;

import java.util.ArrayList;

public class ComputePreds implements InPlaceIRPass<Function> {

    public static final ComputePreds INSTANCE = new ComputePreds();

    @Override
    public void runInPlace(Function func) {
        MetadataState ms = func.getExtOrThrow(CommonExts.METADATA_STATE);

        for (BasicBlock block : func.blocks) {
            block.attachExt(CommonExts.PREDS, new ArrayList<>());
        }
        for (BasicBlock block : func.blocks) {
            for (BasicBlock target : block.getControl().targets) {
                target.getExtOrThrow(CommonExts.PREDS).add(block);
            }
        }

        ms.validate(MetadataState.PREDS);
    }
}

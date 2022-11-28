package io.github.eutro.wasm2j.passes;

import io.github.eutro.wasm2j.passes.form.LowerIntrinsics;
import io.github.eutro.wasm2j.passes.form.LowerPhis;
import io.github.eutro.wasm2j.passes.form.LowerSelects;
import io.github.eutro.wasm2j.passes.meta.InferTypes;
import io.github.eutro.wasm2j.passes.misc.ForPass;
import io.github.eutro.wasm2j.passes.opts.*;
import io.github.eutro.wasm2j.ssa.Function;

public class Passes {
    public static final IRPass<Function, Function> SSA_OPTS =
            ForPass.liftInsns(IdentityElimination.INSTANCE).lift()
                    .then(EliminateDeadVars.INSTANCE);

    public static final IRPass<Function, Function> JAVA_PREEMIT =
            LowerIntrinsics.INSTANCE
                    .then(CollapseJumps.INSTANCE)
                    .then(ForPass.liftBasicBlocks(MergeConds.INSTANCE))
                    .then(LowerSelects.INSTANCE)
                    .then(SSA_OPTS)
                    .then(LowerPhis.INSTANCE)
                    .then(Stackify.INSTANCE)
                    .then(InferTypes.Java.INSTANCE);
}

package io.github.eutro.wasm2j.passes;

import io.github.eutro.wasm2j.passes.meta.InferTypes;
import io.github.eutro.wasm2j.passes.meta.LowerIntrinsics;
import io.github.eutro.wasm2j.passes.meta.LowerPhis;
import io.github.eutro.wasm2j.passes.misc.ForPass;
import io.github.eutro.wasm2j.passes.opts.DeadVarElimination;
import io.github.eutro.wasm2j.passes.opts.IdentityElimination;
import io.github.eutro.wasm2j.passes.opts.Stackify;
import io.github.eutro.wasm2j.ssa.Function;

public class Passes {
    public static final IRPass<Function, Function> SSA_OPTS =
            ForPass.liftInsns(IdentityElimination.INSTANCE).lift()
                    .then(DeadVarElimination.INSTANCE);

    public static final IRPass<Function, Function> JAVA_PREEMIT =
            LowerIntrinsics.INSTANCE
                    .then(LowerPhis.INSTANCE)
                    .then(Stackify.INSTANCE)
                    .then(InferTypes.Java.INSTANCE);
}

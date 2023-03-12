package io.github.eutro.wasm2j.core.passes;

import io.github.eutro.wasm2j.core.passes.form.LinearScan;
import io.github.eutro.wasm2j.core.passes.form.LowerIntrinsics;
import io.github.eutro.wasm2j.core.passes.form.LowerPhis;
import io.github.eutro.wasm2j.core.passes.meta.InferTypes;
import io.github.eutro.wasm2j.core.passes.misc.ForPass;
import io.github.eutro.wasm2j.core.passes.opts.*;
import io.github.eutro.wasm2j.core.ssa.Function;

/**
 * Some pre-composed passes. This should not be considered stable.
 */
public class Passes {
    /**
     * Simple optimisation passes to run on an SSA-form IR.
     */
    public static final IRPass<Function, Function> SSA_OPTS =
            PropagateConstants.INSTANCE
                    .then(ForPass.liftInsns(IdentityElimination.INSTANCE).lift())
                    .then(EliminateDeadVars.INSTANCE);

    /**
     * Passes that must be run before emitting Java bytecode from IR.
     */
    public static final IRPass<Function, Function> JAVA_PREEMIT =
            LowerIntrinsics.INSTANCE
                    .then(CollapseJumps.INSTANCE)
                    .then(MergeConds.INSTANCE)
                    .then(SSA_OPTS)
                    .then(LowerPhis.INSTANCE)
                    .then(Stackify.INSTANCE)
                    .then(InferTypes.Java.INSTANCE)
                    .then(LinearScan.INSTANCE);
}

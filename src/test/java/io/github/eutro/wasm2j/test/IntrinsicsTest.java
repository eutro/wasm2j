package io.github.eutro.wasm2j.test;

import io.github.eutro.wasm2j.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.intrinsics.JavaIntrinsics;
import io.github.eutro.wasm2j.passes.*;
import io.github.eutro.wasm2j.passes.ForPass;
import io.github.eutro.wasm2j.passes.convert.JavaToJir;
import io.github.eutro.wasm2j.passes.opts.DeadVarElimination;
import io.github.eutro.wasm2j.passes.opts.IdentityElimination;
import io.github.eutro.wasm2j.passes.opts.SSAify;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.ssa.display.DisplayInteraction;
import io.github.eutro.wasm2j.ssa.display.SSADisplay;
import org.junit.jupiter.api.Test;

public class IntrinsicsTest {
    @Test
    void testIntrinsicsJir() {
        int i = 0;
        for (IntrinsicImpl value : JavaIntrinsics.INTRINSICS.getValues()) {
            try {
                Function jir = JavaToJir.INSTANCE
                        .then(SSAify.INSTANCE)
                        .then(ForPass.liftInsns(IdentityElimination.INSTANCE).lift())
                        .then(DeadVarElimination.INSTANCE)
                        .run(value.method);
                SSADisplay.debugDisplayToFile(
                        SSADisplay.displaySSA(jir, DisplayInteraction.HIGHLIGHT_INTERESTING),
                        "build/ssa/intr" + i++ + ".svg"
                );
            } catch (Exception error) {
                error.printStackTrace();
            }
        }
    }
}

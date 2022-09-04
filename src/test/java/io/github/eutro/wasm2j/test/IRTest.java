package io.github.eutro.wasm2j.test;

import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.conf.Conventions;
import io.github.eutro.wasm2j.passes.misc.ForPass;
import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.passes.convert.WasmToWir;
import io.github.eutro.wasm2j.passes.convert.WirToJir;
import io.github.eutro.wasm2j.passes.meta.ComputeDomFrontier;
import io.github.eutro.wasm2j.passes.opts.SSAify;
import org.junit.jupiter.api.Test;

public class IRTest {
    void testPass(IRPass<ModuleNode, ?> pass) throws Throwable {
        ModuleNode mn = Utils.getRawModuleNode("/unsimple_bg.wasm");
        pass.run(mn);
    }

    @Test
    void testWir() throws Throwable {
        testPass(WasmToWir.INSTANCE
                .then(ForPass.liftFunctions(
                        ComputeDomFrontier.INSTANCE
                                .then(SSAify.INSTANCE)
                ))
                .then(Utils.debugDisplay("wir")));
    }

    @Test
    void testJir() throws Throwable {
        testPass(WasmToWir.INSTANCE
                .then(ForPass.liftFunctions(
                        ComputeDomFrontier.INSTANCE
                                .then(SSAify.INSTANCE)
                ))
                .then(new WirToJir(Conventions.DEFAULT_CONVENTIONS))
                .then(Utils.debugDisplay("jir")));
    }
}

package io.github.eutro.wasm2j.test;

import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.conf.Conventions;
import io.github.eutro.wasm2j.passes.ForPass;
import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.passes.convert.WasmToWir;
import io.github.eutro.wasm2j.passes.convert.WirToJir;
import io.github.eutro.wasm2j.passes.meta.ComputeDomFrontier;
import io.github.eutro.wasm2j.passes.opts.SSAify;
import io.github.eutro.wasm2j.ssa.display.DisplayInteraction;
import io.github.eutro.wasm2j.ssa.display.SSADisplay;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.ssa.Module;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

public class IRTest {

    @NotNull
    private ModuleNode getRawModuleNode(String name) throws IOException {
        ModuleNode mn = new ModuleNode();
        try (InputStream stream = IRTest.class.getResourceAsStream(name)) {
            ModuleReader.fromInputStream(stream).accept(mn);
        }
        return mn;
    }

    void testPass(IRPass<ModuleNode, ?> pass) throws Throwable {
        ModuleNode mn = getRawModuleNode("/unsimple_bg.wasm");
        pass.run(mn);
    }

    IRPass<Module, Module> debugDisplay(String prefix) {
        return module -> {
            int i = 0;
            for (Function func : module.funcions) {
                SSADisplay.debugDisplayToFile(
                        SSADisplay.displaySSA(func, DisplayInteraction.HIGHLIGHT_INTERESTING),
                        "build/ssa/" + prefix + i + ".svg"
                );
                i++;
            }
            return module;
        };
    }

    @Test
    void testWir() throws Throwable {
        testPass(WasmToWir.INSTANCE
                .then(ForPass.liftFunctions(
                        ComputeDomFrontier.INSTANCE
                                .then(SSAify.INSTANCE)
                ))
                .then(debugDisplay("wir")));
    }

    @Test
    void testJir() throws Throwable {
        testPass(WasmToWir.INSTANCE
                .then(ForPass.liftFunctions(
                        ComputeDomFrontier.INSTANCE
                                .then(SSAify.INSTANCE)
                ))
                .then(new WirToJir(Conventions.DEFAULT_CONVENTIONS))
                .then(debugDisplay("jir")));
    }
}

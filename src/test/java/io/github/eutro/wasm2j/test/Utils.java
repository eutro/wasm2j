package io.github.eutro.wasm2j.test;

import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.display.DisplayInteraction;
import io.github.eutro.wasm2j.ssa.display.SSADisplay;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;

public class Utils {
    @NotNull
    public static ModuleNode getRawModuleNode(String name) throws IOException {
        ModuleNode mn = new ModuleNode();
        try (InputStream stream = IRTest.class.getResourceAsStream(name)) {
            ModuleReader.fromInputStream(stream).accept(mn);
        }
        return mn;
    }

    public static IRPass<Module, Module> debugDisplay(String prefix) {
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
}
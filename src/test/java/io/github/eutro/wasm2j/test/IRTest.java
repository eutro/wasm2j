package io.github.eutro.wasm2j.test;

import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.tree.CodeNode;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.ir.JIR;
import io.github.eutro.wasm2j.ir.SSA;
import io.github.eutro.wasm2j.ir.SSADisplay;
import io.github.eutro.wasm2j.ir.WIR;
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

    @Test
    void testWir() throws Throwable {
        ModuleNode mn = getRawModuleNode("/simple_bg.wasm");
        // mn.accept(new StackDumper(new PrintStream("build/dump.txt")));
        WIR.augmentWithWir(mn);

        if (mn.codes != null) {
            int i = 0;
            for (CodeNode code : mn.codes) {
                SSA.Function func = ((WIR.WIRExprNode) code.expr).wir;
                SSADisplay.debugDisplayToFile(
                        SSADisplay.displaySSA(func),
                        "build/ssa/ssa" + i + ".svg"
                );
                i++;
            }
        }
    }

    @Test
    void testJir() throws Throwable {
        ModuleNode mn = getRawModuleNode("/simple_bg.wasm");
        WIR.augmentWithWir(mn);
        JIR.Class jirClass = JIR.convertFromWir(mn);
        int i = 0;
        for (JIR.Method method : jirClass.methods) {
            SSADisplay.debugDisplayToFile(
                    SSADisplay.displaySSA(method.func),
                    "build/ssa/jir" + i + ".svg"
            );
            i++;
        }
    }
}

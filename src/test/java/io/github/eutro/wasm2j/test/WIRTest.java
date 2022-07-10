package io.github.eutro.wasm2j.test;

import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.tree.CodeNode;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.ir.WIR;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

public class WIRTest {
    @Test
    void testWir() throws Throwable {
        ModuleNode mn = new ModuleNode();
        try (InputStream stream = WIRTest.class.getResourceAsStream("/aoc_bg.wasm")) {
            ModuleReader.fromInputStream(stream).accept(mn);
        }
        // mn.accept(new StackDumper(new PrintStream("build/dump.txt")));
        WIR.augmentWithWir(mn);

        if (mn.codes != null) {
            for (CodeNode code : mn.codes) {
                System.out.println("-- WIR");
                System.out.println(((WIR.WIRExprNode) code.expr).wir);
            }
        }
    }
}

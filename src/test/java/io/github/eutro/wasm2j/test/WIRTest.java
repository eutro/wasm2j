package io.github.eutro.wasm2j.test;

import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.tree.*;
import io.github.eutro.jwasm.tree.analysis.StackDumper;
import io.github.eutro.wasm2j.ir.WIR;
import org.junit.jupiter.api.Test;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;

public class WIRTest {
    @Test
    void testWir() throws Throwable {
        ModuleNode mn = new ModuleNode();
        try (InputStream stream = WIRTest.class.getResourceAsStream("/unsimple_bg.wasm")) {
            ModuleReader.fromInputStream(stream).accept(mn);
        }
        WIR.augmentWithWir(mn);

        if (mn.codes != null) {
            for (CodeNode code : mn.codes) {
                System.out.println("-- WIR");
                System.out.println(((WIR.WIRExprNode) code.expr).wir);
            }
        }
    }
}

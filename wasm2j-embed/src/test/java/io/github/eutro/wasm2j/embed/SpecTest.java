package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.sexp.wast.WastReader;
import io.github.eutro.jwasm.test.ModuleTestBase;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

public class SpecTest {

    public static final File DEBUG_OUTPUT_DIRECTORY = new File("build/wasmout");
    public static final String SOURCE = "" +
            "(module\n" +
            "  (memory (data \"\\aa\\bb\\cc\\dd\"))\n" +
            "\n" +
            "  (func (export \"copy\") (param i32 i32 i32)\n" +
            "    (memory.copy\n" +
            "      (local.get 0)\n" +
            "      (local.get 1)\n" +
            "      (local.get 2)))\n" +
            "\n" +
            "  (func (export \"load8_u\") (param i32) (result i32)\n" +
            "    (i32.load8_u (local.get 0)))\n" +
            ")\n" +
            "\n" +
            ";; Non-overlapping copy.\n" +
            "(invoke \"copy\" (i32.const 10) (i32.const 0) (i32.const 4))\n" +
            "\n" +
            "(assert_return (invoke \"load8_u\" (i32.const 9)) (i32.const 0))\n" +
            "(assert_return (invoke \"load8_u\" (i32.const 10)) (i32.const 0xaa))";

    @Test
    void inlineTest() {
        WebAssembly wasm = new WebAssembly();
        wasm.setDebugOutputDirectory(DEBUG_OUTPUT_DIRECTORY);
        WastReader.fromSource(SOURCE).accept(new ExecutingWastVisitor(wasm));
    }

    @TestFactory
    Stream<DynamicTest> specTest() throws IOException {
        WebAssembly wasm = new WebAssembly();
        wasm.setDebugOutputDirectory(DEBUG_OUTPUT_DIRECTORY);
        return ModuleTestBase.openTestSuite()
                .filter(it -> it.getName().indexOf('/') == -1 && it.getName().endsWith(".wast"))
                .filter(it -> !it.getName().startsWith("simd_"))
                .map(it -> DynamicTest.dynamicTest(it.getName(), () -> {
                    WastReader wastReader = WastReader.fromSource(it.getStream());
                    Assertions.assertDoesNotThrow(() -> wastReader.accept(new ExecutingWastVisitor(wasm)));
                }));
    }
}

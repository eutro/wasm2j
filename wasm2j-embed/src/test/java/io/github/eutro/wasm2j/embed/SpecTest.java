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
            "  (memory (export \"memory0\") 1 1)\n" +
            "  (data (i32.const 2) \"\\03\\01\\04\\01\")\n" +
            "  (data \"\\02\\07\\01\\08\")\n" +
            "  (data (i32.const 12) \"\\07\\05\\02\\03\\06\")\n" +
            "  (data \"\\05\\09\\02\\07\\06\")\n" +
            "  (func (export \"test\")\n" +
            "    (memory.init 1 (i32.const 7) (i32.const 0) (i32.const 4))\n" +
            "    (data.drop 1)\n" +
            "    (memory.init 3 (i32.const 15) (i32.const 1) (i32.const 3))\n" +
            "    (data.drop 3)\n" +
            "    (memory.copy (i32.const 20) (i32.const 15) (i32.const 5))\n" +
            "    (memory.copy (i32.const 21) (i32.const 29) (i32.const 1))\n" +
            "    (memory.copy (i32.const 24) (i32.const 10) (i32.const 1))\n" +
            "    (memory.copy (i32.const 13) (i32.const 11) (i32.const 4))\n" +
            "    (memory.copy (i32.const 19) (i32.const 20) (i32.const 5)))\n" +
            "  (func (export \"load8_u\") (param i32) (result i32)\n" +
            "    (i32.load8_u (local.get 0))))\n" +
            "\n" +
            "(invoke \"test\")\n" +
            "\n" +
            "(assert_return (invoke \"load8_u\" (i32.const 0)) (i32.const 0))\n" +
            "(assert_return (invoke \"load8_u\" (i32.const 1)) (i32.const 0))\n" +
            "(assert_return (invoke \"load8_u\" (i32.const 2)) (i32.const 3))\n" +
            "(assert_return (invoke \"load8_u\" (i32.const 3)) (i32.const 1))\n" +
            "(assert_return (invoke \"load8_u\" (i32.const 4)) (i32.const 4))\n" +
            "(assert_return (invoke \"load8_u\" (i32.const 5)) (i32.const 1))\n" +
            "(assert_return (invoke \"load8_u\" (i32.const 6)) (i32.const 0))\n" +
            "(assert_return (invoke \"load8_u\" (i32.const 7)) (i32.const 2))\n" +
            "(assert_return (invoke \"load8_u\" (i32.const 8)) (i32.const 7))\n" +
            "(assert_return (invoke \"load8_u\" (i32.const 9)) (i32.const 1))\n" +
            "(assert_return (invoke \"load8_u\" (i32.const 10)) (i32.const 8))\n" +
            "(assert_return (invoke \"load8_u\" (i32.const 11)) (i32.const 0))\n" +
            "(assert_return (invoke \"load8_u\" (i32.const 12)) (i32.const 7))\n" +
            "(assert_return (invoke \"load8_u\" (i32.const 13)) (i32.const 0))";

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
                .map(it -> DynamicTest.dynamicTest(it.getName(), () -> {
                    WastReader wastReader = WastReader.fromSource(it.getStream());
                    Assertions.assertDoesNotThrow(() -> wastReader.accept(new ExecutingWastVisitor(wasm)));
                }));
    }
}

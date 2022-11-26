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
            "  (table 1 funcref)\n" +
            "  (func $f)\n" +
            "  (elem $p funcref (ref.func $f))\n" +
            "  (elem $a (table 0) (i32.const 0) func $f)\n" +
            "\n" +
            "  (func (export \"drop_passive\") (elem.drop $p))\n" +
            "  (func (export \"init_passive\") (param $len i32)\n" +
            "    (table.init $p (i32.const 0) (i32.const 0) (local.get $len))\n" +
            "  )\n" +
            "\n" +
            "  (func (export \"drop_active\") (elem.drop $a))\n" +
            "  (func (export \"init_active\") (param $len i32)\n" +
            "    (table.init $a (i32.const 0) (i32.const 0) (local.get $len))\n" +
            "  )\n" +
            ")";

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

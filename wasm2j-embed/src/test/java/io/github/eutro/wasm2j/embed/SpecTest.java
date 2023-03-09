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
            "  (memory 1)\n" +
            "  (data (i32.const 0) \"abcdefghijklmnopqrstuvwxyz\")\n" +
            "  (func (export \"32u_bad\") (param $i i32)\n" +
            "    (drop (i64.load32_u offset=4294967295 (local.get $i)))\n" +
            "  )" +
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
                .filter(it -> !it.getName().startsWith("simd_")) // TODO simd
                .map(it -> DynamicTest.dynamicTest(it.getName(), () -> {
                    WastReader wastReader = WastReader.fromSource(it.getStream());
                    Assertions.assertDoesNotThrow(() -> wastReader.accept(new ExecutingWastVisitor(wasm)));
                }));
    }
}

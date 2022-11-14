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
            "  (table $i (import \"spectest\" \"table\") 1 2 funcref)\n" +
            "  (table (export \"table\") 1 funcref)\n" +
            "  (func $f)\n" +
            "  (global funcref (ref.func $f))\n" +
            "  (func (export \"testops\") (result funcref i32 i32)\n" +
            "    (table.set $i (i32.const 0) (ref.func $f))\n" +
            "    (table.get $i (i32.const 0))\n" +
            "    (table.grow $i (ref.null func) (i32.const 1))\n" +
            "    (table.size $i))\n" +

            "  (memory $m 1 2)\n" +
            "  (func (export \"grow\") (param $by i32) (result i32)" +
            "    (memory.grow (local.get $by)))\n" +
            ")\n" +
            "\n";

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

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
            "(module $Mt\n" +
            "  (type (func (result i32)))\n" +
            "  (type (func))\n" +
            "\n" +
            "  (table (export \"tab\") 10 funcref)\n" +
            "  (elem (i32.const 2) $g $g $g $g)\n" +
            "  (func $g (result i32) (i32.const 4))\n" +
            "  (func (export \"h\") (result i32) (i32.const -4))\n" +
            "\n" +
            "  (func (export \"call\") (param i32) (result i32)\n" +
            "    (call_indirect (type 0) (local.get 0))\n" +
            "  )\n" +
            ")\n" +
            "(register \"Mt\" $Mt)\n" +
            "\n" +
            ";; Unlike in the v1 spec, active element segments stored before an\n" +
            ";; out-of-bounds access persist after the instantiation failure.\n" +
            "(assert_trap\n" +
            "  (module\n" +
            "    (table (import \"Mt\" \"tab\") 10 funcref)\n" +
            "    (func $f (result i32) (i32.const 0))\n" +
            "    (elem (i32.const 7) $f)\n" +
            "    (elem (i32.const 8) $f $f $f $f $f)  ;; (partially) out of bounds\n" +
            "  )\n" +
            "  \"out of bounds table access\"\n" +
            ")\n" +
            "(assert_return (invoke $Mt \"call\" (i32.const 7)) (i32.const 0))\n" +
            "(assert_trap (invoke $Mt \"call\" (i32.const 8)) \"uninitialized element\")";

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

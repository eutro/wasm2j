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
            "  (type (func (result i32)))\n" +
            "  (table 128 128 funcref)\n" +
            "  (elem (i32.const 112)\n" +
            "         $f0 $f1 $f2 $f3 $f4 $f5 $f6 $f7 $f8 $f9 $f10 $f11 $f12 $f13 $f14 $f15)\n" +
            "  (func $f0 (export \"f0\") (result i32) (i32.const 0))\n" +
            "  (func $f1 (export \"f1\") (result i32) (i32.const 1))\n" +
            "  (func $f2 (export \"f2\") (result i32) (i32.const 2))\n" +
            "  (func $f3 (export \"f3\") (result i32) (i32.const 3))\n" +
            "  (func $f4 (export \"f4\") (result i32) (i32.const 4))\n" +
            "  (func $f5 (export \"f5\") (result i32) (i32.const 5))\n" +
            "  (func $f6 (export \"f6\") (result i32) (i32.const 6))\n" +
            "  (func $f7 (export \"f7\") (result i32) (i32.const 7))\n" +
            "  (func $f8 (export \"f8\") (result i32) (i32.const 8))\n" +
            "  (func $f9 (export \"f9\") (result i32) (i32.const 9))\n" +
            "  (func $f10 (export \"f10\") (result i32) (i32.const 10))\n" +
            "  (func $f11 (export \"f11\") (result i32) (i32.const 11))\n" +
            "  (func $f12 (export \"f12\") (result i32) (i32.const 12))\n" +
            "  (func $f13 (export \"f13\") (result i32) (i32.const 13))\n" +
            "  (func $f14 (export \"f14\") (result i32) (i32.const 14))\n" +
            "  (func $f15 (export \"f15\") (result i32) (i32.const 15))\n" +
            "  (func (export \"test\") (param $n i32) (result i32)\n" +
            "    (call_indirect (type 0) (local.get $n)))\n" +
            "  (func (export \"run\") (param $targetOffs i32) (param $srcOffs i32) (param $len i32)\n" +
            "    (table.copy (local.get $targetOffs) (local.get $srcOffs) (local.get $len))))\n" +
            "\n" +
            "(assert_trap (invoke \"run\" (i32.const 0) (i32.const 112) (i32.const 4294967264))\n" +
            "             \"out of bounds table access\")\n" +
            "(assert_trap (invoke \"test\" (i32.const 0)) \"uninitialized element\")";

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

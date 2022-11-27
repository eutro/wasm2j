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
            "  (func (export \"ef0\") (result i32) (i32.const 0))\n" +
            "  (func (export \"ef1\") (result i32) (i32.const 1))\n" +
            "  (func (export \"ef2\") (result i32) (i32.const 2))\n" +
            "  (func (export \"ef3\") (result i32) (i32.const 3))\n" +
            "  (func (export \"ef4\") (result i32) (i32.const 4))\n" +
            ")\n" +
            "(register \"a\")" +
            "(module\n" +
            "  (type (func (result i32)))  ;; type #0\n" +
            "  (import \"a\" \"ef0\" (func (result i32)))    ;; index 0\n" +
            "  (import \"a\" \"ef1\" (func (result i32)))\n" +
            "  (import \"a\" \"ef2\" (func (result i32)))\n" +
            "  (import \"a\" \"ef3\" (func (result i32)))\n" +
            "  (import \"a\" \"ef4\" (func (result i32)))    ;; index 4\n" +
            "  (table $t0 30 30 funcref)\n" +
            "  (table $t1 30 30 funcref)\n" +
            "  (elem (table $t1) (i32.const 2) func 3 1 4 1)\n" +
            "  (elem funcref\n" +
            "    (ref.func 2) (ref.func 7) (ref.func 1) (ref.func 8))\n" +
            "  (elem (table $t1) (i32.const 12) func 7 5 2 3 6)\n" +
            "  (elem funcref\n" +
            "    (ref.func 5) (ref.func 9) (ref.func 2) (ref.func 7) (ref.func 6))\n" +
            "  (elem (table $t0) (i32.const 3) func 1 3 1 4)\n" +
            "  (elem (table $t0) (i32.const 11) func 6 3 2 5 7)\n" +
            "  (func (result i32) (i32.const 5))  ;; index 5\n" +
            "  (func (result i32) (i32.const 6))\n" +
            "  (func (result i32) (i32.const 7))\n" +
            "  (func (result i32) (i32.const 8))\n" +
            "  (func (result i32) (i32.const 9))  ;; index 9\n" +
            "  (func (export \"test\")\n" +
            "    (nop))\n" +
            "  (func (export \"check_t0\") (param i32) (result i32)\n" +
            "    (call_indirect $t1 (type 0) (local.get 0)))\n" +
            "  (func (export \"check_t1\") (param i32) (result i32)\n" +
            "    (call_indirect $t0 (type 0) (local.get 0)))\n" +
            ")\n" +
            "\n" +
            "(invoke \"test\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 0)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 1)) \"uninitialized element\")\n" +
            "(assert_return (invoke \"check_t0\" (i32.const 2)) (i32.const 3))\n" +
            "(assert_return (invoke \"check_t0\" (i32.const 3)) (i32.const 1))\n" +
            "(assert_return (invoke \"check_t0\" (i32.const 4)) (i32.const 4))\n" +
            "(assert_return (invoke \"check_t0\" (i32.const 5)) (i32.const 1))\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 6)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 7)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 8)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 9)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 10)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 11)) \"uninitialized element\")\n" +
            "(assert_return (invoke \"check_t0\" (i32.const 12)) (i32.const 7))\n" +
            "(assert_return (invoke \"check_t0\" (i32.const 13)) (i32.const 5))\n" +
            "(assert_return (invoke \"check_t0\" (i32.const 14)) (i32.const 2))\n" +
            "(assert_return (invoke \"check_t0\" (i32.const 15)) (i32.const 3))\n" +
            "(assert_return (invoke \"check_t0\" (i32.const 16)) (i32.const 6))\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 17)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 18)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 19)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 20)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 21)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 22)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 23)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 24)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 25)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 26)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 27)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 28)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t0\" (i32.const 29)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 0)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 1)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 2)) \"uninitialized element\")\n" +
            "(assert_return (invoke \"check_t1\" (i32.const 3)) (i32.const 1))\n" +
            "(assert_return (invoke \"check_t1\" (i32.const 4)) (i32.const 3))\n" +
            "(assert_return (invoke \"check_t1\" (i32.const 5)) (i32.const 1))\n" +
            "(assert_return (invoke \"check_t1\" (i32.const 6)) (i32.const 4))\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 7)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 8)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 9)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 10)) \"uninitialized element\")\n" +
            "(assert_return (invoke \"check_t1\" (i32.const 11)) (i32.const 6))\n" +
            "(assert_return (invoke \"check_t1\" (i32.const 12)) (i32.const 3))\n" +
            "(assert_return (invoke \"check_t1\" (i32.const 13)) (i32.const 2))\n" +
            "(assert_return (invoke \"check_t1\" (i32.const 14)) (i32.const 5))\n" +
            "(assert_return (invoke \"check_t1\" (i32.const 15)) (i32.const 7))\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 16)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 17)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 18)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 19)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 20)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 21)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 22)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 23)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 24)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 25)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 26)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 27)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 28)) \"uninitialized element\")\n" +
            "(assert_trap (invoke \"check_t1\" (i32.const 29)) \"uninitialized element\")";

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

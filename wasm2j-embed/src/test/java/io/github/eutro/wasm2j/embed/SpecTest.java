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
            "  (func $f1 (export \"funcref\") (param $x funcref) (result i32)\n" +
            "    (ref.is_null (local.get $x))\n" +
            "  )\n" +
            "  (func $f2 (export \"externref\") (param $x externref) (result i32)\n" +
            "    (ref.is_null (local.get $x))\n" +
            "  )\n" +
            "\n" +
            "  (table $t1 2 funcref)\n" +
            "  (table $t2 2 externref)\n" +
            "  (elem (table $t1) (i32.const 1) func $dummy)\n" +
            "  (func $dummy)\n" +
            "\n" +
            "  (func (export \"init\") (param $r externref)\n" +
            "    (table.set $t2 (i32.const 1) (local.get $r))\n" +
            "  )\n" +
            "  (func (export \"deinit\")\n" +
            "    (table.set $t1 (i32.const 1) (ref.null func))\n" +
            "    (table.set $t2 (i32.const 1) (ref.null extern))\n" +
            "  )\n" +
            "\n" +
            "  (func (export \"funcref-elem\") (param $x i32) (result i32)\n" +
            "    (call $f1 (table.get $t1 (local.get $x)))\n" +
            "  )\n" +
            "  (func (export \"externref-elem\") (param $x i32) (result i32)\n" +
            "    (call $f2 (table.get $t2 (local.get $x)))\n" +
            "  )\n" +
            ")\n" +
            "\n" +
            "(assert_return (invoke \"funcref\" (ref.null func)) (i32.const 1))\n" +
            "(assert_return (invoke \"externref\" (ref.null extern)) (i32.const 1))\n" +
            "\n" +
            "(assert_return (invoke \"externref\" (ref.extern 1)) (i32.const 0))\n" +
            "\n" +
            "(invoke \"init\" (ref.extern 0))\n" +
            "\n" +
            "(assert_return (invoke \"funcref-elem\" (i32.const 0)) (i32.const 1))\n" +
            "(assert_return (invoke \"externref-elem\" (i32.const 0)) (i32.const 1))\n" +
            "\n" +
            "(assert_return (invoke \"funcref-elem\" (i32.const 1)) (i32.const 0))\n" +
            "(assert_return (invoke \"externref-elem\" (i32.const 1)) (i32.const 0))\n" +
            "\n" +
            "(invoke \"deinit\")\n" +
            "\n" +
            "(assert_return (invoke \"funcref-elem\" (i32.const 0)) (i32.const 1))\n" +
            "(assert_return (invoke \"externref-elem\" (i32.const 0)) (i32.const 1))\n" +
            "\n" +
            "(assert_return (invoke \"funcref-elem\" (i32.const 1)) (i32.const 1))\n" +
            "(assert_return (invoke \"externref-elem\" (i32.const 1)) (i32.const 1))\n" +
            "\n" +
            "(assert_invalid\n" +
            "  (module (func $ref-vs-num (param i32) (ref.is_null (local.get 0))))\n" +
            "  \"type mismatch\"\n" +
            ")\n" +
            "(assert_invalid\n" +
            "  (module (func $ref-vs-empty (ref.is_null)))\n" +
            "  \"type mismatch\"\n" +
            ")\n";

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

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
            "  (memory 1 1)\n" +
            "  (func (export \"init\") (param $i i32) (param $x f32) (f32.store (local.get $i) (local.get $x)))\n" +
            "\n" +
            "  (func (export \"run\") (param $n i32) (param $z f32)\n" +
            "    (local $i i32)\n" +
            "    (block $exit\n" +
            "      (loop $cont\n" +
            "        (f32.store\n" +
            "          (local.get $i)\n" +
            "          (f32.div (f32.load (local.get $i)) (local.get $z))\n" +
            "        )\n" +
            "        (local.set $i (i32.add (local.get $i) (i32.const 4)))\n" +
            "        (br_if $cont (i32.lt_u (local.get $i) (local.get $n)))\n" +
            "      )\n" +
            "    )\n" +
            "  )\n" +
            "\n" +
            "  (func (export \"check\") (param $i i32) (result f32) (f32.load (local.get $i)))\n" +
            ")\n" +
            "\n" +
            "(invoke \"init\" (i32.const  0) (f32.const 15.1))\n" +
            "(invoke \"init\" (i32.const  4) (f32.const 15.2))\n" +
            "(invoke \"init\" (i32.const  8) (f32.const 15.3))\n" +
            "(invoke \"init\" (i32.const 12) (f32.const 15.4))\n" +
            "(assert_return (invoke \"check\" (i32.const  0)) (f32.const 15.1))\n" +
            "(assert_return (invoke \"check\" (i32.const  4)) (f32.const 15.2))\n" +
            "(assert_return (invoke \"check\" (i32.const  8)) (f32.const 15.3))\n" +
            "(assert_return (invoke \"check\" (i32.const 12)) (f32.const 15.4))\n" +
            "(invoke \"run\" (i32.const 16) (f32.const 3.0))\n" +
            "(assert_return (invoke \"check\" (i32.const  0)) (f32.const 0x1.422222p+2))\n" +
            "(assert_return (invoke \"check\" (i32.const  4)) (f32.const 0x1.444444p+2))\n" +
            "(assert_return (invoke \"check\" (i32.const  8)) (f32.const 0x1.466666p+2))\n" +
            "(assert_return (invoke \"check\" (i32.const 12)) (f32.const 0x1.488888p+2))";

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

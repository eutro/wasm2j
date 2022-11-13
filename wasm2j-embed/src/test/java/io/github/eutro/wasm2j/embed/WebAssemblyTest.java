package io.github.eutro.wasm2j.embed;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.function.IntBinaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebAssemblyTest {
    @Test
    void testStuffs() throws Throwable {
        WebAssembly wasm = new WebAssembly();
        wasm.setDebugOutputDirectory(new File("build/wasmout"));

        Store store = wasm.storeInit();
        Module module = wasm.moduleParse(
                "(module" +
                        "  (import \"externs\" \"bar\" (func $bar (param i32) (param i32) (result i32)))" +
                        "  (import \"externs\" \"print\" (func $print (param i32)))" +
                        "  (func $start (call $print (i32.const 100)))" +
                        "  (start $start)" +
                        "  (func (export \"how\"))" +
                        "  (func (export \"foo\") (result i32)" +
                        "    (call $bar (i32.const 2) (i32.const 10000))" +
                        "  )" +
                        ")"
        );
        ModuleInst inst = wasm.moduleInstantiate(store, module, new ExternVal[]{
                ExternVal.func(IntBinaryOperator.class, (x, y) -> x * y),
                ExternVal.func((int x) -> System.out.println(x))
        });
        assertEquals(20000, (int) inst.getExport("foo").getAsFuncRaw().invokeExact());
    }
}
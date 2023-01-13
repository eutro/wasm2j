package io.github.eutro.wasm2j.embed;

import org.junit.jupiter.api.Test;

import java.io.File;

class WebAssemblyTest {
    @Test
    void testStuffs() throws Throwable {
        WebAssembly wasm = new WebAssembly();
        wasm.setDebugOutputDirectory(new File("build/wasmout"));

        Store store = wasm.storeInit();
        Module module = wasm.moduleParse(
                "(module" +
                        "  (func $how (result i32) (i32.trunc_f32_s (f32.const 1.0)))" +
                        ")"
        );
        Instance inst = wasm.moduleInstantiate(store, module, new ExternVal[]{});
    }
}
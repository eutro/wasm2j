package io.github.eutro.wasm2j.test;

import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.conf.Conventions;
import io.github.eutro.wasm2j.passes.convert.JirToJava;
import io.github.eutro.wasm2j.passes.convert.WasmToWir;
import io.github.eutro.wasm2j.passes.convert.WirToJir;
import io.github.eutro.wasm2j.passes.meta.InferTypes;
import io.github.eutro.wasm2j.passes.meta.LowerIntrinsics;
import io.github.eutro.wasm2j.passes.meta.LowerPhis;
import io.github.eutro.wasm2j.passes.misc.ForPass;
import io.github.eutro.wasm2j.passes.opts.Stackify;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileOutputStream;

public class FullTest {
    @Test
    void testFull() throws Throwable {
        ModuleNode mn = Utils.getRawModuleNode("/basic_bg.wasm");
        ClassNode node = WasmToWir.INSTANCE
                .then(new WirToJir(Conventions.DEFAULT_CONVENTIONS))
                .then(ForPass.liftFunctions(
                        LowerIntrinsics.INSTANCE
                                .then(LowerPhis.INSTANCE)
                                .then(Stackify.INSTANCE)))
                .then(ForPass.liftBasicBlocks(InferTypes.Java.INSTANCE).lift())
                .then(JirToJava.INSTANCE)
                .run(mn);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(cw);

        byte[] classBytes = cw.toByteArray();
        File file = new File("build/wasmout/" + node.name + ".class");
        file.getParentFile().mkdirs();
        try (FileOutputStream os = new FileOutputStream(file)) {
            os.write(classBytes);
        }
    }
}

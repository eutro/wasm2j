package io.github.eutro.wasm2j.test;

import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.conf.Conventions;
import io.github.eutro.wasm2j.passes.Passes;
import io.github.eutro.wasm2j.passes.convert.JirToJava;
import io.github.eutro.wasm2j.passes.convert.WasmToWir;
import io.github.eutro.wasm2j.passes.convert.WirToJir;
import io.github.eutro.wasm2j.passes.meta.*;
import io.github.eutro.wasm2j.passes.misc.ForPass;
import io.github.eutro.wasm2j.passes.meta.VerifyIntegrity;
import io.github.eutro.wasm2j.passes.opts.SSAify;
import io.github.eutro.wasm2j.passes.opts.Stackify;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileOutputStream;

public class FullTest {
    @Test
    void testFull() throws Throwable {
        ModuleNode mn = Utils.getRawModuleNode("/unsimple_bg.wasm");
        ClassNode node = WasmToWir.INSTANCE
                .then(ForPass.liftFunctions(SSAify.INSTANCE))
                .then(Utils.debugDisplay("wasm"))
                .then(new WirToJir(Conventions.DEFAULT_CONVENTIONS))
                .then(ForPass.liftFunctions(Passes.SSA_OPTS))
                .then(Utils.debugDisplay("preop"))
                .then(ForPass.liftFunctions(LowerIntrinsics.INSTANCE
                        .then(LowerPhis.INSTANCE)
                        .then(Stackify.INSTANCE)))
                .then(ForPass.liftFunctions(ComputeDomFrontier.INSTANCE))
                .then(Utils.debugDisplay("postop"))

                .then(ForPass.liftFunctions(InferTypes.Java.INSTANCE))

                .then(ForPass.liftFunctions(VerifyIntegrity.INSTANCE))

                .then(JirToJava.INSTANCE)
                .then(CheckJava.INSTANCE)
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

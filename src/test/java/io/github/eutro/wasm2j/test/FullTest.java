package io.github.eutro.wasm2j.test;

import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.conf.Conventions;
import io.github.eutro.wasm2j.passes.Passes;
import io.github.eutro.wasm2j.passes.convert.JirToJava;
import io.github.eutro.wasm2j.passes.convert.WasmToWir;
import io.github.eutro.wasm2j.passes.convert.WirToJir;
import io.github.eutro.wasm2j.passes.form.*;
import io.github.eutro.wasm2j.passes.meta.CheckJava;
import io.github.eutro.wasm2j.passes.meta.ComputeDomFrontier;
import io.github.eutro.wasm2j.passes.meta.InferTypes;
import io.github.eutro.wasm2j.passes.meta.VerifyIntegrity;
import io.github.eutro.wasm2j.passes.misc.ForPass;
import io.github.eutro.wasm2j.passes.opts.MergeConds;
import io.github.eutro.wasm2j.passes.opts.Stackify;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicReference;

public class FullTest {
    @Test
    void testFull() throws Throwable {
        ModuleNode mn = Utils.getRawModuleNode("/aoc_bg.wasm");
        /*
        assert mn.codes != null && mn.codes.codes != null;
        ListIterator<CodeNode> it = mn.codes.codes.listIterator();
        while (it.hasNext()) {
            CodeNode code = it.next();
            if (it.previousIndex() != 11) {
                code.expr.instructions = Collections.singletonList(new InsnNode(Opcodes.UNREACHABLE));
            }
        }
         */

        ClassNode node = WasmToWir.INSTANCE
                //.then(Utils.debugDisplay("wasm"))
                .then(ForPass.liftFunctions(SSAify.INSTANCE))
                .then(new WirToJir(Conventions.DEFAULT_CONVENTIONS))
                .then(ForPass.liftFunctions(Passes.SSA_OPTS))
                //.then(Utils.debugDisplay("preop"))
                .then(ForPass.liftFunctions(LowerIntrinsics.INSTANCE))
                .then(ForPass.liftFunctions(ComputeDomFrontier.INSTANCE))
                //.then(Utils.debugDisplay("postop"))
                .then(ForPass.liftFunctions(Utils.debugDisplayOnError("lower",
                        ForPass.liftBasicBlocks(MergeConds.INSTANCE)
                                .then(LowerSelects.INSTANCE)
                                .then(LowerPhis.INSTANCE)
                                .then(Stackify.INSTANCE))))

                .then(ForPass.liftFunctions(Utils.debugDisplayOnError("infer", InferTypes.Java.INSTANCE)))
                .then(ForPass.liftFunctions(LinearScan.INSTANCE))

                .then(ForPass.liftFunctions(VerifyIntegrity.INSTANCE))

                //.then(ForPass.liftFunctions(ComputeDomFrontier.INSTANCE))
                //.then(Utils.debugDisplay("preemit"))
                .then(JirToJava.INSTANCE)
                .then(CheckJava.INSTANCE)
                .run(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);

        byte[] classBytes = cw.toByteArray();

        File file = new File("build/wasmout/" + node.name + ".class");
        file.getParentFile().mkdirs();
        try (FileOutputStream os = new FileOutputStream(file)) {
            os.write(classBytes);
        }

        AtomicReference<Class<?>> clazzRef = new AtomicReference<>();
        new ClassLoader() {{
            clazzRef.set(defineClass("com.example.FIXME", classBytes, 0, classBytes.length));
        }};
        Class<?> clazz = clazzRef.get();
        clazz.getConstructor().newInstance();
    }
}

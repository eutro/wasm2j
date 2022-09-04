package io.github.eutro.wasm2j.test;

import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.intrinsics.JavaIntrinsics;
import io.github.eutro.wasm2j.passes.*;
import io.github.eutro.wasm2j.passes.meta.InferTypes;
import io.github.eutro.wasm2j.passes.meta.LowerPhis;
import io.github.eutro.wasm2j.passes.misc.ForPass;
import io.github.eutro.wasm2j.passes.convert.JavaToJir;
import io.github.eutro.wasm2j.passes.convert.JirToJava;
import io.github.eutro.wasm2j.passes.misc.JoinPass;
import io.github.eutro.wasm2j.passes.opts.DeadVarElimination;
import io.github.eutro.wasm2j.passes.opts.IdentityElimination;
import io.github.eutro.wasm2j.passes.opts.SSAify;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.display.DisplayInteraction;
import io.github.eutro.wasm2j.ssa.display.SSADisplay;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class IntrinsicsTest {
    @Test
    void testIntrinsicsJir() {
        int i = 0;
        IRPass<MethodNode, Function> pass = JavaToJir.INSTANCE
                .then(SSAify.INSTANCE)
                .then(ForPass.liftInsns(IdentityElimination.INSTANCE).lift())
                .then(DeadVarElimination.INSTANCE);
        for (IntrinsicImpl intr : JavaIntrinsics.INTRINSICS.getValues()) {
            try {
                Function jir = pass
                        .run(intr.method);
                SSADisplay.debugDisplayToFile(
                        SSADisplay.displaySSA(jir, DisplayInteraction.HIGHLIGHT_INTERESTING),
                        "build/ssa/intr" + i++ + ".svg"
                );
            } catch (Exception error) {
                error.printStackTrace();
            }
        }
    }

    @Test
    void testIntrinsicsRoundTrip() throws IOException {
        int i = 0;
        IRPass<MethodNode, ClassNode> pass =
                new JoinPass<>(
                        JavaToJir.INSTANCE
                                .then(SSAify.INSTANCE)
                                .then(ForPass.liftInsns(IdentityElimination.INSTANCE).lift())
                                .then(DeadVarElimination.INSTANCE)
                                .then(ForPass.liftBasicBlocks(InferTypes.Java.INSTANCE))
                                .then(LowerPhis.INSTANCE),
                        mn -> {
                            JavaExts.JavaClass clazz = new JavaExts.JavaClass("dev/eutro/Test");
                            clazz.methods.add(new JavaExts.JavaMethod(
                                    clazz,
                                    mn.name,
                                    mn.desc,
                                    JavaExts.JavaMethod.Type.STATIC
                            ));
                            return clazz;
                        },
                        (function, clazz) -> {
                            clazz.methods.get(0).attachExt(JavaExts.METHOD_IMPL, function);
                            Module md = new Module();
                            md.attachExt(JavaExts.JAVA_CLASS, clazz);
                            return md;
                        }
                )
                        .then(JirToJava.INSTANCE);
        for (IntrinsicImpl intr : JavaIntrinsics.INTRINSICS.getValues()) {
            ClassNode node = pass.run(intr.method);
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(cw);
            byte[] classBytes = cw.toByteArray();
            File fileName = new File("build/jbytes/intr" + i++ + ".class");
            fileName.getParentFile().mkdirs();
            try (FileOutputStream os = new FileOutputStream(fileName)) {
                os.write(classBytes);
            }
        }
    }
}

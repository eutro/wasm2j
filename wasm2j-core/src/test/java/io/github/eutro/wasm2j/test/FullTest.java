package io.github.eutro.wasm2j.test;

import io.github.eutro.jwasm.Opcodes;
import io.github.eutro.jwasm.tree.CodeNode;
import io.github.eutro.jwasm.tree.InsnNode;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.conf.Conventions;
import io.github.eutro.wasm2j.conf.Getters;
import io.github.eutro.wasm2j.conf.Imports;
import io.github.eutro.wasm2j.ext.JavaExts;
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
import io.github.eutro.wasm2j.passes.opts.CollapseJumps;
import io.github.eutro.wasm2j.passes.opts.MergeConds;
import io.github.eutro.wasm2j.passes.opts.Stackify;
import io.github.eutro.wasm2j.ssa.display.SSADisplay;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.ListIterator;
import java.util.concurrent.atomic.AtomicReference;

public class FullTest {
    public static Integer OMIT_EXCEPT = null;
    public static boolean SAVE_CODE = true;

    public static IOImpl IO_IMPL = new IOImpl();

    @SuppressWarnings("unused") // reflected
    public static class IOImpl {
        static InputStream is = System.in;
        static OutputStream os = System.out;

        public int stdin_read_byte() throws IOException {
            return is.read();
        }

        public void stdout_write_byte(int b) throws IOException {
            os.write(b);
        }

        public void throw_exn() {
            throw new RuntimeException("panicked");
        }
    }

    @Test
    void testFull() throws Throwable {
        ModuleNode mn = Utils.getRawModuleNode("/aoc_bg.wasm");
        if (OMIT_EXCEPT != null) {
            assert mn.codes != null && mn.codes.codes != null;
            ListIterator<CodeNode> it = mn.codes.codes.listIterator();
            while (it.hasNext()) {
                CodeNode code = it.next();
                if (it.previousIndex() != OMIT_EXCEPT) {
                    code.expr.instructions = Collections.singletonList(new InsnNode(Opcodes.UNREACHABLE));
                }
            }
        }

        ClassNode node = WasmToWir.INSTANCE
                //.then(Utils.debugDisplay("wasm"))
                .then(ForPass.liftFunctions(SSAify.INSTANCE))
                .then(new WirToJir(Conventions.createBuilder()
                        .setFunctionImports(Imports.interfaceFuncImports(
                                Getters.staticGetter(
                                        JavaExts.JavaField.fromJava(
                                                JavaExts.JavaClass.fromJava(FullTest.class),
                                                FullTest.class.getField("IO_IMPL")
                                        )
                                ),
                                JavaExts.JavaClass.fromJava(IOImpl.class),
                                Conventions.DEFAULT_CC
                        ))
                        .build()))
                .then(ForPass.liftFunctions(Passes.SSA_OPTS))
                //.then(Utils.debugDisplay("preop"))
                .then(ForPass.liftFunctions(LowerIntrinsics.INSTANCE))
                .then(ForPass.liftFunctions(ComputeDomFrontier.INSTANCE))
                //.then(Utils.debugDisplay("postop"))
                .then(ForPass.liftFunctions(CollapseJumps.INSTANCE))
                .then(ForPass.liftFunctions(SSADisplay.debugDisplayOnError("lower",
                        ForPass.liftBasicBlocks(MergeConds.INSTANCE)
                                .then(LowerSelects.INSTANCE)
                                .then(LowerPhis.INSTANCE)
                                .then(Stackify.INSTANCE))))

                .then(ForPass.liftFunctions(SSADisplay.debugDisplayOnError("infer", InferTypes.Java.INSTANCE)))
                //.then(ForPass.liftFunctions(LinearScan.INSTANCE))

                .then(ForPass.liftFunctions(SSADisplay.debugDisplayOnError("verify", VerifyIntegrity.INSTANCE)))

                //.then(ForPass.liftFunctions(ComputeDomFrontier.INSTANCE))
                //.then(Utils.debugDisplay("preemit"))
                .then(JirToJava.INSTANCE)
                .then(CheckJava.INSTANCE)
                .run(mn);

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);

        byte[] classBytes = cw.toByteArray();

        if (SAVE_CODE) {
            File file = new File("build/wasmout/" + node.name + ".class");
            file.getParentFile().mkdirs();
            try (FileOutputStream os = new FileOutputStream(file)) {
                os.write(classBytes);
            }
        }

        AtomicReference<Class<?>> clazzRef = new AtomicReference<>();
        new ClassLoader() {{
            clazzRef.set(defineClass("com.example.FIXME", classBytes, 0, classBytes.length));
        }};

        Class<?> clazz = clazzRef.get();
        Object instance = clazz.getConstructor().newInstance();

        Method setup_panic = clazz.getMethod("setup_panic");
        setup_panic.invoke(instance);

        Method day_00 = clazz.getMethod("day_00");
        day_00.invoke(instance);
        System.out.flush();

        //Method day_01 = clazz.getMethod("day_01");
        //try (InputStream is = FullTest.class.getResourceAsStream("/input/1.txt")) {
        //    IOImpl.is = is;
        //    day_01.invoke(instance);
        //    System.out.flush();
        //} finally {
        //    IOImpl.is = System.in;
        //}
    }
}

package io.github.eutro.wasm2j.test;

import io.github.eutro.wasm2j.core.ext.JavaExts;
import io.github.eutro.wasm2j.core.intrinsics.Intrinsic;
import io.github.eutro.wasm2j.core.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.core.intrinsics.JavaIntrinsics;
import io.github.eutro.wasm2j.core.intrinsics.impls.Operators;
import io.github.eutro.wasm2j.core.passes.IRPass;
import io.github.eutro.wasm2j.core.passes.Passes;
import io.github.eutro.wasm2j.core.passes.convert.JavaToJir;
import io.github.eutro.wasm2j.core.passes.convert.JirToJava;
import io.github.eutro.wasm2j.core.passes.form.SSAify;
import io.github.eutro.wasm2j.core.ssa.Function;
import io.github.eutro.wasm2j.core.ssa.JClass;
import io.github.eutro.wasm2j.core.ssa.display.SSADisplay;
import io.github.eutro.wasm2j.core.util.Lazy;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.*;

public class IntrinsicsTest {

    public static final int LOOP_COUNT = 20_000;

    @Test
    void testIntrinsicsJir() {
        int i = 0;
        for (IntrinsicImpl intr : JavaIntrinsics.INTRINSICS.getValues()) {
            Function jir = intr.impl;
            if (jir == null) continue;
            SSADisplay.debugDisplayToFile(
                    SSADisplay.displaySSA(jir),
                    "build/ssa/intr" + i++ + ".svg"
            );
        }
    }

    @Test
    void testIntrinsicsRoundTrip() throws IOException {
        IRPass<MethodNode, ClassNode> pass = mn -> {
            JClass clazz = new JClass("intrinsics/" + mn.name);
            clazz.methods.add(new JClass.JavaMethod(
                    clazz,
                    mn.name,
                    mn.desc,
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
            ));
            clazz.methods.get(0).attachExt(JavaExts.METHOD_IMPL, Lazy.lazy(() ->
                    JavaToJir.INSTANCE
                            .then(SSAify.INSTANCE)
                            .then(Passes.SSA_OPTS)
                            .then(Passes.JAVA_PREEMIT).run(mn)));
            return JirToJava.INSTANCE.run(clazz);
        };
        for (IntrinsicImpl intr : JavaIntrinsics.INTRINSICS.getValues()) {
            ClassNode node = pass.run(intr.method);
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(cw);
            byte[] classBytes = cw.toByteArray();
            File fileName = new File("build/jbytes/" + intr.method.name + ".class");
            boolean ignored = fileName.getParentFile().mkdirs();
            try (FileOutputStream os = new FileOutputStream(fileName)) {
                os.write(classBytes);
            }
        }
    }

    @Test
    void testIntrinsicsAssembly() throws ReflectiveOperationException {
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        Map<Class<?>, MethodHandle> rngs = new HashMap<>();

        Random rand = new Random();
        rngs.put(int.class, lookup
                .findVirtual(Random.class, "nextInt", MethodType.methodType(int.class))
                .bindTo(rand));
        rngs.put(long.class, lookup
                .findVirtual(Random.class, "nextLong", MethodType.methodType(long.class))
                .bindTo(rand));
        rngs.put(float.class, lookup
                .findVirtual(Random.class, "nextFloat", MethodType.methodType(float.class))
                .bindTo(rand));
        rngs.put(double.class, lookup
                .findVirtual(Random.class, "nextDouble", MethodType.methodType(double.class))
                .bindTo(rand));

        List<MethodHandle> invokers = new ArrayList<>();
        for (Method method : Operators.class.getMethods()) {
            if (method.getAnnotation(Intrinsic.class) == null) continue;

            MethodHandle handle = lookup.unreflect(method);

            for (Class<?> pType : method.getParameterTypes()) {
                MethodHandle rng = rngs.get(pType);
                if (rng == null) {
                    throw new IllegalStateException();
                }
                handle = MethodHandles.collectArguments(handle, 0, rng);
            }

            invokers.add(handle);
        }

        for (MethodHandle invoker : invokers) {
            for (int i = 0; i < LOOP_COUNT; i++) {
                try {
                    invoker.invoke();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    @Test
    void testIntrinsicsValues() {
        Assertions.assertEquals(1L, Operators.i64TruncF32U(1F));
        Assertions.assertEquals(0x8000000000000000L, Operators.i64TruncF64U(9.223372036854776E18));
    }

    @Test
    void testIntrinsicsTraps() {
        Assertions.assertThrows(RuntimeException.class, () -> Operators.i32TruncF32S(2.14748365E9F));
    }

    public static void main(String[] args) throws Throwable {
        new IntrinsicsTest().testIntrinsicsAssembly();
    }
}

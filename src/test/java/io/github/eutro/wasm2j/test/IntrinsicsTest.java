package io.github.eutro.wasm2j.test;

import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.intrinsics.Impl;
import io.github.eutro.wasm2j.intrinsics.Intrinsic;
import io.github.eutro.wasm2j.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.intrinsics.JavaIntrinsics;
import io.github.eutro.wasm2j.passes.*;
import io.github.eutro.wasm2j.passes.convert.JavaToJir;
import io.github.eutro.wasm2j.passes.convert.JirToJava;
import io.github.eutro.wasm2j.passes.misc.JoinPass;
import io.github.eutro.wasm2j.passes.form.SSAify;
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
                    SSADisplay.displaySSA(jir, DisplayInteraction.HIGHLIGHT_INTERESTING),
                    "build/ssa/intr" + i++ + ".svg"
            );
        }
    }

    @Test
    void testIntrinsicsRoundTrip() throws IOException {
        IRPass<MethodNode, ClassNode> pass =
                new JoinPass<>(
                        JavaToJir.INSTANCE
                                .then(SSAify.INSTANCE)
                                .then(Passes.SSA_OPTS)
                                .then(Passes.JAVA_PREEMIT),
                        mn -> {
                            JavaExts.JavaClass clazz = new JavaExts.JavaClass("intrinsics/" + mn.name);
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
            File fileName = new File("build/jbytes/" + intr.method.name + ".class");
            fileName.getParentFile().mkdirs();
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
        for (Method method : Impl.class.getMethods()) {
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

        List<List<Object>> allResults = new ArrayList<>();
        for (MethodHandle invoker : invokers) {
            Object[] results = new Object[LOOP_COUNT];
            for (int i = 0; i < LOOP_COUNT; i++) {
                try {
                    results[i] = invoker.invoke();
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
            allResults.add(Arrays.asList(results));
        }
        // System.out.println(allResults);
    }

    public static void main(String[] args) throws Throwable {
        new IntrinsicsTest().testIntrinsicsAssembly();
    }
}

package io.github.eutro.wasm2j.test;

import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.intrinsics.ImplClassBytes;
import io.github.eutro.wasm2j.passes.Passes;
import io.github.eutro.wasm2j.passes.convert.Handlify;
import io.github.eutro.wasm2j.passes.convert.JavaToJir;
import io.github.eutro.wasm2j.passes.convert.JirToJava;
import io.github.eutro.wasm2j.passes.form.SSAify;
import io.github.eutro.wasm2j.passes.opts.CollapseJumps;
import io.github.eutro.wasm2j.passes.opts.EliminateDeadBlocks;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.ssa.JClass;
import io.github.eutro.wasm2j.util.Lazy;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.BitSet;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class HandlifyTest {
    @Test
    void testHandlify() throws Throwable {
        ClassNode cn = new ClassNode();
        ImplClassBytes.getClassReaderFor(HandlifyTest.class).accept(cn, 0);
        MethodNode testMethod = null;
        for (MethodNode method : cn.methods) {
            if (method.name.equals("testMethod")) {
                testMethod = method;
                break;
            }
        }
        assertNotNull(testMethod);

        BitSet keepFree = new BitSet();
        keepFree.set(0, true);
        Function handlifyFunc = JavaToJir.INSTANCE
                .then(SSAify.INSTANCE)
                .then(Passes.SSA_OPTS)
                .then(CollapseJumps.INSTANCE)
                .then(EliminateDeadBlocks.INSTANCE)
                .then(new Handlify(keepFree))
                .run(testMethod);
        assertNotNull(handlifyFunc);
        JClass jClass = new JClass("Handlify");
        JClass.JavaMethod jMethod = new JClass.JavaMethod(
                jClass, "testMethodHandle", "(I)Ljava/lang/invoke/MethodHandle;",
                JClass.JavaMethod.Kind.STATIC);
        jClass.methods.add(jMethod);
        handlifyFunc.attachExt(JavaExts.FUNCTION_METHOD, jMethod);
        jMethod.attachExt(JavaExts.METHOD_IMPL, Lazy.lazy(() -> handlifyFunc));
        Passes.JAVA_PREEMIT.run(handlifyFunc);

        ClassNode outCn = JirToJava.INSTANCE.run(jClass);
        outCn.access |= Opcodes.ACC_PUBLIC;

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        outCn.accept(cw);
        byte[] bytes = cw.toByteArray();
        // Files.write(new File("build/handlify/Handlify.class").toPath(), bytes);
        Class<?> clazz = new ClassLoader() {
            Class<?> define() {
                return defineClass(outCn.name.replace('/', '.'), bytes, 0, bytes.length);
            }
        }.define();

        Object mh = clazz.getMethod("testMethodHandle", int.class).invoke(null, 10);
        assertInstanceOf(MethodHandle.class, mh);
        MethodHandle mhMh = (MethodHandle) mh;
        mhMh.invoke(1);
    }

    public static ByteBuffer buf = ByteBuffer.allocate(100);

    public static void setBuf(ByteBuffer buf) {
        HandlifyTest.buf = buf;
    }

    public static void println(int v) {
        System.out.println(v);
    }

    static void testMethod(int x, int y) {

    }
}

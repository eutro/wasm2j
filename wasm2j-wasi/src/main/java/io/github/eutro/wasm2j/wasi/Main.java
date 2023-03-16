package io.github.eutro.wasm2j.wasi;

import io.github.eutro.wasm2j.api.ModuleCompilation;
import io.github.eutro.wasm2j.api.WasmCompiler;
import io.github.eutro.wasm2j.api.bits.FormatDetector;
import io.github.eutro.wasm2j.api.bits.InterfaceBasedLinker;
import io.github.eutro.wasm2j.api.events.EmitClassEvent;
import io.github.eutro.wasm2j.api.support.CaseStyle;
import io.github.eutro.wasm2j.api.support.NameMangler;
import io.github.eutro.wasm2j.api.support.NameSupplier;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.Arrays;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: wasm2j-wasi [file] [args ...]");
            return;
        }
        String fileName = args[0];
        WasmCompiler cc = new WasmCompiler();
        InterfaceBasedLinker<WasmCompiler> ibl = cc.add(new InterfaceBasedLinker<>(NameSupplier.createSimple(
                "io/github/eutro/wasm2j/wasi/",
                NameMangler.jvmUnqualified(NameMangler.IllegalSymbolPolicy.MANGLE_BIJECTIVE),
                CaseStyle.detect(CaseStyle.LOWER_SNAKE), CaseStyle.UPPER_CAMEL,
                CaseStyle.detect(CaseStyle.LOWER_SNAKE), CaseStyle.LOWER_CAMEL
        )));
        WasiImpl wasi = new WasiImpl(Arrays.copyOfRange(args, 1, args.length));
        Loader loader = new Loader();
        ibl.listen(EmitClassEvent.class, ece -> {
            if ("io/github/eutro/wasm2j/wasi/WasiSnapshotPreview1".equals(ece.classNode.name)) {
                return; // it already exists
            }
            Class<?> newClass = loader.loadBytes(ece.classNode);
            if (ece.classNode.name.endsWith("WasiMain")) {
                try {
                    Constructor<?> ctor = newClass.getConstructor(WasiSnapshotPreview1.class);

                    Object inst = ctor.newInstance(wasi);
                    Method getMemory = newClass.getMethod("getMemory");
                    MethodHandle getMemoryH = MethodHandles.lookup().unreflect(getMemory);
                    wasi.mem = getMemoryH.bindTo(inst);

                    Method startMethod = newClass.getMethod("Start");
                    try {
                        startMethod.invoke(inst);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        });
        ModuleCompilation comp = new FormatDetector(cc).submitFile(Paths.get(fileName));
        comp.setName("io/github/eutro/wasm2j/wasi/WasiMain");
        ibl.register("wasi_main_itf", comp.node);
        comp.run();
        ibl.finish();
    }

    private static class WasiImpl extends WasiSnapshotPreview1Impl {
        private final byte[][] args;
        MethodHandle mem;

        public WasiImpl(String[] args) {
            byte[][] bytes = new byte[args.length][];
            for (int i = 0; i < args.length; i++) {
                bytes[i] = args[i].getBytes(StandardCharsets.UTF_8);
            }
            this.args = bytes;
        }

        @Override
        protected ByteBuffer mem() {
            try {
                return (ByteBuffer) mem.invokeExact();
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }

        @Override
        protected byte[][] getArgs() {
            return args;
        }
    }

    private static class Loader extends ClassLoader {
        public Loader() {
        }

        public Class<?> loadBytes(ClassNode node) {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            node.accept(cw);
            byte[] bytes = cw.toByteArray();
            return defineClass(node.name.replace('/', '.'), bytes, 0, bytes.length);
        }
    }
}

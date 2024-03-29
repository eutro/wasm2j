package io.github.eutro.wasm2j.test;

import io.github.eutro.jwasm.sexp.WatParser;
import io.github.eutro.jwasm.sexp.wast.WastModuleVisitor;
import io.github.eutro.jwasm.sexp.wast.WastReader;
import io.github.eutro.jwasm.sexp.wast.WastVisitor;
import io.github.eutro.jwasm.test.ModuleTestBase;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.jwasm.tree.analysis.ModuleValidator;
import io.github.eutro.wasm2j.core.conf.itf.WirJavaConventionFactory;
import io.github.eutro.wasm2j.core.ext.JavaExts;
import io.github.eutro.wasm2j.core.passes.IRPass;
import io.github.eutro.wasm2j.core.passes.Passes;
import io.github.eutro.wasm2j.core.passes.convert.JirToJava;
import io.github.eutro.wasm2j.core.passes.convert.WasmToWir;
import io.github.eutro.wasm2j.core.passes.convert.WirToJir;
import io.github.eutro.wasm2j.core.passes.meta.CheckJava;
import io.github.eutro.wasm2j.core.passes.meta.VerifyIntegrity;
import io.github.eutro.wasm2j.core.ssa.JClass;
import io.github.eutro.wasm2j.core.ssa.display.SSADisplay;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodTooLargeException;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Method;
import java.util.stream.Stream;

public class SpecTest {

    public static final IRPass<ModuleNode, ClassNode> PASS = WasmToWir.INSTANCE
            .then(new WirToJir(WirJavaConventionFactory.builder()
                    .setNameSupplier(() -> "dev/eutro/Example")
                    .build()))
            .then(cls -> {
                for (JClass.JavaMethod method : cls.methods) {
                    method.getExtOrThrow(JavaExts.METHOD_IMPL)
                            .mapInPlace(Passes.SSA_OPTS
                                    .then(Passes.JAVA_PREEMIT)
                                    .then(SSADisplay.debugDisplayOnError("verify", VerifyIntegrity.INSTANCE))
                                    ::run);
                }
                return cls;
            })
            .then(JirToJava.INSTANCE)
            .then(CheckJava.INSTANCE);

    @Test
    void testInline() {
        WastReader.fromSource("(module\n" +
                        "  (func (export \"param\") (result i32)\n" +
                        "    (i32.const 1)\n" +
                        "    (block (param i32) (result i32)\n" +
                        "      (i32.const 2)\n" +
                        "      (i32.add)\n" +
                        "    )\n" +
                        "  )" +
                        ")")
                .accept(new LoadingWastVisitor());
    }

    @TestFactory
    Stream<DynamicTest> specTest() throws Throwable {
        return ModuleTestBase.openTestSuite()
                .filter(it -> it.getName().indexOf('/') == -1 && it.getName().endsWith(".wast"))
                .filter(it -> !it.getName().startsWith("simd_"))
                .map(it -> DynamicTest.dynamicTest(it.getName(), () -> {
                    WastReader wastReader = WastReader.fromSource(it.getStream());
                    Assertions.assertDoesNotThrow(() -> wastReader.accept(new LoadingWastVisitor()));
                }));
    }

    private static class LoadingWastVisitor extends WastVisitor {
        ModuleNode lastModule;

        int tmc = 0;

        @Override
        public WastModuleVisitor visitModule(@Nullable String name) {
            tmc++;
            return new WastModuleVisitor() {
                @Override
                public void visitWatModule(Object module) {
                    lastModule = WatParser.DEFAULT.parseModule(module);
                }

                @Override
                public void visitBinaryModule(Object module) {
                    lastModule = WatParser.DEFAULT.parseBinaryModule(module);
                }

                @Override
                public void visitQuoteModule(Object module) {
                    lastModule = WatParser.DEFAULT.parseQuoteModule(module);
                }

                @Override
                public void visitEnd() {
                    try {
                        lastModule.accept(new ModuleValidator());
                        ClassNode code = PASS.run(lastModule);
                        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
                        code.accept(cw);
                        Class<?> theClass = new ClassLoader() {
                            Class<?> defineTheClass() {
                                byte[] bytes = cw.toByteArray();
                                return defineClass(
                                        code.name.replace('/', '.'),
                                        bytes,
                                        0,
                                        bytes.length
                                );
                            }
                        }.defineTheClass();
                        Method[] ignored = theClass.getMethods(); // verify
                    } catch (UnsupportedOperationException ignored) {
                        // TODO support all the operations
                    } catch (MethodTooLargeException ignored) {
                        // this is okay, but we should do something better with it...
                    } catch (Throwable t) {
                        t.addSuppressed(new RuntimeException("in top module #" + tmc));
                        throw t;
                    }
                }
            };
        }
    }
}

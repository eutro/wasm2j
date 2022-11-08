package io.github.eutro.wasm2j.test;

import io.github.eutro.jwasm.sexp.Parser;
import io.github.eutro.jwasm.sexp.wast.WastModuleVisitor;
import io.github.eutro.jwasm.sexp.wast.WastReader;
import io.github.eutro.jwasm.sexp.wast.WastVisitor;
import io.github.eutro.jwasm.test.ModuleTestBase;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.jwasm.tree.analysis.ModuleValidator;
import io.github.eutro.wasm2j.conf.Conventions;
import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.passes.Passes;
import io.github.eutro.wasm2j.passes.convert.JirToJava;
import io.github.eutro.wasm2j.passes.convert.WasmToWir;
import io.github.eutro.wasm2j.passes.convert.WirToJir;
import io.github.eutro.wasm2j.passes.form.LowerIntrinsics;
import io.github.eutro.wasm2j.passes.form.LowerPhis;
import io.github.eutro.wasm2j.passes.form.LowerSelects;
import io.github.eutro.wasm2j.passes.form.SSAify;
import io.github.eutro.wasm2j.passes.meta.CheckJava;
import io.github.eutro.wasm2j.passes.meta.ComputeDomFrontier;
import io.github.eutro.wasm2j.passes.meta.InferTypes;
import io.github.eutro.wasm2j.passes.meta.VerifyIntegrity;
import io.github.eutro.wasm2j.passes.misc.ForPass;
import io.github.eutro.wasm2j.passes.opts.CollapseJumps;
import io.github.eutro.wasm2j.passes.opts.MergeConds;
import io.github.eutro.wasm2j.passes.opts.Stackify;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.util.stream.Stream;

public class SpecTest {

    public static final IRPass<ModuleNode, ClassNode> PASS = WasmToWir.INSTANCE
            .then(Utils.debugDisplay("wasm"))
            .then(ForPass.liftFunctions(SSAify.INSTANCE))
            .then(new WirToJir(Conventions.createBuilder().build()))
            .then(ForPass.liftFunctions(Passes.SSA_OPTS))
            .then(Utils.debugDisplay("preop"))
            .then(ForPass.liftFunctions(LowerIntrinsics.INSTANCE))
            .then(ForPass.liftFunctions(ComputeDomFrontier.INSTANCE))
            .then(Utils.debugDisplay("postop"))
            .then(ForPass.liftFunctions(CollapseJumps.INSTANCE))
            .then(ForPass.liftFunctions(Utils.debugDisplayOnError("lower",
                    ForPass.liftBasicBlocks(MergeConds.INSTANCE)
                            .then(LowerSelects.INSTANCE)
                            .then(LowerPhis.INSTANCE)
                            .then(Stackify.INSTANCE))))

            .then(ForPass.liftFunctions(Utils.debugDisplayOnError("infer", InferTypes.Java.INSTANCE)))
            //.then(ForPass.liftFunctions(LinearScan.INSTANCE))

            .then(ForPass.liftFunctions(Utils.debugDisplayOnError("verify", VerifyIntegrity.INSTANCE)))

            //.then(ForPass.liftFunctions(ComputeDomFrontier.INSTANCE))
            //.then(Utils.debugDisplay("preemit"))
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
                .map(it -> DynamicTest.dynamicTest(it.getName(), () -> {
                    WastReader wastReader = WastReader.fromSource(it.getStream());
                    Assertions.assertDoesNotThrow(() -> wastReader.accept(new LoadingWastVisitor()));
                }));
    }

    private static class LoadingWastVisitor extends WastVisitor {
        ModuleNode lastModule;

        int tmc = 0;

        @Override
        public WastModuleVisitor visitModule() {
            tmc++;
            return new WastModuleVisitor() {
                @Override
                public void visitWatModule(Object module) {
                    lastModule = Parser.parseModule(module);
                }

                @Override
                public void visitBinaryModule(Object module) {
                    lastModule = Parser.parseBinaryModule(module);
                }

                @Override
                public void visitQuoteModule(Object module) {
                    lastModule = Parser.parseQuoteModule(module);
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
                        System.out.println(theClass);
                    } catch (Throwable t) {
                        t.addSuppressed(new RuntimeException("in top module #" + tmc));
                        throw t;
                    }
                }
            };
        }
    }
}

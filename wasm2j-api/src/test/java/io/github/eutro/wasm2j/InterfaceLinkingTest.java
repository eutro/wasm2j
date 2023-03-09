package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.ByteInputStream;
import io.github.eutro.jwasm.test.ModuleTestBase;
import io.github.eutro.wasm2j.bits.InterfaceBasedLinker;
import io.github.eutro.wasm2j.events.EmitClassEvent;
import io.github.eutro.wasm2j.support.CaseStyle;
import io.github.eutro.wasm2j.support.NameMangler;
import io.github.eutro.wasm2j.support.NameSupplier;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;

import static io.github.eutro.wasm2j.support.NameMangler.IllegalTokenPolicy.MANGLE_BIJECTIVE;

public class InterfaceLinkingTest {

    public static final String PACKAGE = "io/github/eutro/test/";

    @Test
    void test() throws IOException {
        WasmCompiler cc = new WasmCompiler();
        InterfaceBasedLinker<?> ibl = cc.add(new InterfaceBasedLinker<>(NameSupplier.createSimple(
                PACKAGE,
                NameMangler.javaIdent(MANGLE_BIJECTIVE),
                CaseStyle.LOWER_SNAKE, CaseStyle.UPPER_CAMEL,
                CaseStyle.LOWER_SNAKE, CaseStyle.LOWER_CAMEL
        )));
        ibl.listen(EmitClassEvent.class, ece -> {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            ece.classNode.accept(new CheckClassAdapter(cw));
            byte[] bytes = cw.toByteArray();
            File file = new File("./build/ilt/" + ece.classNode.name + ".class");
            file.getParentFile().mkdirs();
            try {
                Files.write(file.toPath(), bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        ModuleCompilation comp = cc.submitText("(module" +
                "(func $bar (import \"foo\" \"bar\"))" +
                "(global $glob (import \"foo\" \"glob\") (mut i32))" +
                "(memory $mem (export \"mem\") (import \"mem\" \"mem\") 2)" +
                "(func $main" +
                "  (global.set $glob (i32.const 100))" +
                "  (call $bar))" +
                "(start $main))");
        ibl.register("main_itf", comp.node);
        comp.setName(PACKAGE + "Main").run();

        try (InputStream is = ModuleTestBase.openResource(ModuleTestBase.AOC_SOLNS)) {
            comp = cc.submitBinary(new ByteInputStream.InputStreamByteInputStream(is));
            comp.setName(PACKAGE + "Aoc");
            ibl.register("aoc_itf", comp.node);
            comp.run();
        }

        ibl.finish();
    }
}
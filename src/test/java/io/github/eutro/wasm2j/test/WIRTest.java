package io.github.eutro.wasm2j.test;

import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.tree.*;
import io.github.eutro.wasm2j.ir.WIR;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

public class WIRTest {
    @Test
    void testWir() throws Throwable {
        ModuleNode mn = new ModuleNode();
        try (InputStream stream = WIRTest.class.getResourceAsStream("/unsimple_bg.wasm")) {
            ModuleReader.fromInputStream(stream).accept(mn);
        }
        assert mn.imports == null;
        assert mn.funcs != null;
        assert mn.funcs.funcs != null;
        assert mn.types != null;
        assert mn.types.types != null;
        assert mn.codes != null;

        FuncNode[] referencableFuncs = mn.funcs.funcs.toArray(new FuncNode[0]);
        int i = 0;
        TypeNode[] funcTypes = mn.types.types.toArray(new TypeNode[0]);
        for (CodeNode code : mn.codes) {
            WIR.Function wir = WIR.convert(
                    funcTypes,
                    referencableFuncs,
                    code.locals,
                    mn.types.types.get(mn.funcs.funcs.get(i).type).returns.length,
                    code.expr
            );
            System.out.println("--- WIR");
            System.out.println(wir);
        }
    }
}

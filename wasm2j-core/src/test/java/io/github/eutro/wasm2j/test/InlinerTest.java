package io.github.eutro.wasm2j.test;

import io.github.eutro.wasm2j.core.intrinsics.ImplClassBytes;
import io.github.eutro.wasm2j.core.ops.CommonOps;
import io.github.eutro.wasm2j.core.passes.IRPass;
import io.github.eutro.wasm2j.core.passes.convert.JavaToJir;
import io.github.eutro.wasm2j.core.passes.form.SSAify;
import io.github.eutro.wasm2j.core.ssa.Function;
import io.github.eutro.wasm2j.core.ssa.IRBuilder;
import io.github.eutro.wasm2j.core.ssa.Inliner;
import io.github.eutro.wasm2j.core.ssa.Var;
import io.github.eutro.wasm2j.core.ssa.display.SSADisplay;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class InlinerTest {
    @SuppressWarnings("unused")
    private static class Tested {
        public static int add(int lhs, int rhs) {
            return lhs + rhs;
        }

        public static int mul(int lhs, int rhs) {
            return lhs * rhs;
        }
    }

    @Test
    void testInliner() {
        ClassNode cn = new ClassNode();
        ImplClassBytes.getClassReaderFor(Tested.class).accept(cn, ClassReader.SKIP_DEBUG);

        Map<String, MethodNode> methodMap = new HashMap<>();
        for (MethodNode method : cn.methods) {
            if ((method.access & Opcodes.ACC_PUBLIC) != 0) {
                methodMap.put(method.name, method);
            }
        }
        IRPass<MethodNode, Function> pass =
                JavaToJir.INSTANCE
                        .then(SSAify.INSTANCE);

        Map<String, Function> funcMap = new HashMap<>();
        methodMap.forEach((s, methodNode) ->
                funcMap.put(s, pass.run(methodNode)));

        Function add = funcMap.get("add");
        Function mul = funcMap.get("mul");
        SSADisplay.debugDisplayToFile(SSADisplay.displaySSA(add), "build/ssa/add.svg");
        SSADisplay.debugDisplayToFile(SSADisplay.displaySSA(mul), "build/ssa/mul.svg");

        Function addAndMul = new Function();
        IRBuilder ib = new IRBuilder(addAndMul, addAndMul.newBb());
        Var lhs = ib.insert(CommonOps.ARG.create(0).insn(), "lhs");
        Var rhs = ib.insert(CommonOps.ARG.create(1).insn(), "rhs");
        Var res = ib.insert(new Inliner(ib).inline(add, Arrays.asList(lhs, rhs)), "res");
        Var ret = ib.insert(new Inliner(ib).inline(mul, Arrays.asList(res, rhs)), "ret");
        ib.insertCtrl(CommonOps.RETURN.insn(ret).jumpsTo());

        SSADisplay.debugDisplayToFile(
                SSADisplay.displaySSA(addAndMul),
                "build/ssa/inlined.svg"
        );
    }
}

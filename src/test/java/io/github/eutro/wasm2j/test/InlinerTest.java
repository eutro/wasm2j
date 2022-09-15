package io.github.eutro.wasm2j.test;

import io.github.eutro.wasm2j.intrinsics.ImplClassBytes;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.passes.convert.JavaToJir;
import io.github.eutro.wasm2j.passes.form.SSAify;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.ssa.Inliner;
import io.github.eutro.wasm2j.ssa.Var;
import io.github.eutro.wasm2j.ssa.display.SSADisplay;
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
        BasicBlock bb = addAndMul.newBb();
        Var lhs = addAndMul.newVar("lhs");
        bb.addEffect(CommonOps.ARG.create(0).insn().assignTo(lhs));
        Var rhs = addAndMul.newVar("rhs");
        bb.addEffect(CommonOps.ARG.create(1).insn().assignTo(rhs));
        Var res = addAndMul.newVar("res");
        BasicBlock nbb;
        nbb = addAndMul.newBb();
        nbb.addEffect(new Inliner(add, addAndMul)
                .inline(Arrays.asList(lhs, rhs), bb, nbb)
                .assignTo(res));
        bb = nbb;
        Var ret = addAndMul.newVar("ret");
        nbb = addAndMul.newBb();
        nbb.addEffect(new Inliner(mul, addAndMul)
                .inline(Arrays.asList(res, rhs), bb, nbb)
                .assignTo(ret));
        bb = nbb;
        bb.setControl(CommonOps.RETURN.insn(ret).jumpsTo());

        SSADisplay.debugDisplayToFile(
                SSADisplay.displaySSA(addAndMul),
                "build/ssa/inlined.svg"
        );
    }
}

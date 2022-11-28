package io.github.eutro.wasm2j.util;

import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

public class Instructions {
    public static InsnList copyList(InsnList insns) {
        MethodNode mn = new MethodNode();
        insns.accept(mn);
        return mn.instructions;
    }
}

package io.github.eutro.wasm2j.util;

import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodNode;

/**
 * Static utilities for Java instructions. This API should not be considered stable.
 */
public class Instructions {
    /**
     * Copy a list of instructions.
     *
     * @param insns The list of instructions to copy.
     * @return The copied instructions.
     */
    public static InsnList copyList(InsnList insns) {
        MethodNode mn = new MethodNode();
        insns.accept(mn);
        return mn.instructions;
    }
}

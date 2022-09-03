package io.github.eutro.wasm2j.intrinsics;

import org.objectweb.asm.tree.MethodNode;

public class IntrinsicImpl {
    public final MethodNode method;
    public final boolean inline;

    public IntrinsicImpl(MethodNode method, boolean inline) {
        this.method = method;
        this.inline = inline;
    }
}

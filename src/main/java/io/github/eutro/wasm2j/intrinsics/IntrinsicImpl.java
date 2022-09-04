package io.github.eutro.wasm2j.intrinsics;

import io.github.eutro.wasm2j.passes.convert.JavaToJir;
import io.github.eutro.wasm2j.passes.convert.JirToJava;
import io.github.eutro.wasm2j.passes.misc.ForPass;
import io.github.eutro.wasm2j.passes.opts.DeadVarElimination;
import io.github.eutro.wasm2j.passes.opts.IdentityElimination;
import io.github.eutro.wasm2j.passes.opts.SSAify;
import io.github.eutro.wasm2j.ssa.Function;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

public class IntrinsicImpl {
    public final MethodNode method;
    public final Function impl;
    public final boolean inline;

    public IntrinsicImpl(MethodNode method, boolean inline) {
        this.method = method;
        this.inline = inline;
        if (!inline) {
            impl = null;
        } else {
            impl = JavaToJir.INSTANCE
                    .then(SSAify.INSTANCE)
                    .then(ForPass.liftInsns(IdentityElimination.INSTANCE).lift())
                    .then(DeadVarElimination.INSTANCE)
                    .run(method);
        }
    }

    public Type[] getReturnTypes() {
        return new Type[]{Type.getReturnType(method.desc)};
    }

    @Override
    public String toString() {
        return method.name;
    }
}

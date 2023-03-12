package io.github.eutro.wasm2j.intrinsics;

import io.github.eutro.wasm2j.passes.convert.JavaToJir;
import io.github.eutro.wasm2j.passes.form.SSAify;
import io.github.eutro.wasm2j.passes.misc.ForPass;
import io.github.eutro.wasm2j.passes.opts.*;
import io.github.eutro.wasm2j.ssa.Function;
import org.objectweb.asm.tree.MethodNode;

import java.lang.invoke.MethodHandle;

/**
 * An implementation of an intrinsic.
 */
public class IntrinsicImpl {
    /**
     * The method node from which the intrinsic was parsed.
     */
    public final MethodNode method;

    /**
     * The implementation of the method, for inlining. May be null if not {@link #inline}.
     */
    public final Function impl;

    /**
     * Whether the intrinsic should be inlined into its caller.
     */
    public final boolean inline;
    /**
     * A method handle that can be invoked to evaluate the intrinsic at compile time.
     */
    public MethodHandle eval = null;

    /**
     * Create an intrinsic from the given method node.
     * <p>
     * If {@code inline} is true, the code of the method will be converted
     * to Java IR. Not all Java code can be processed in this way,
     * so it may fail if called with arbitrary Java methods.
     *
     * @param method The method node.
     * @param inline Whether the intrinsic should be inlined into its caller.
     */
    public IntrinsicImpl(MethodNode method, boolean inline) {
        this.method = method;
        this.inline = inline;
        if (!inline) {
            impl = null;
        } else {
            impl = JavaToJir.INSTANCE
                    .then(SSAify.INSTANCE)
                    .then(FindBoolSelects.INSTANCE)
                    .then(EliminateDeadBlocks.INSTANCE)
                    .then(CollapseJumps.INSTANCE)
                    .then(ForPass.liftInsns(IdentityElimination.INSTANCE).lift())
                    .then(EliminateDeadVars.INSTANCE)
                    .run(method);
        }
    }

    @Override
    public String toString() {
        return method.name;
    }
}

package io.github.eutro.wasm2j.ext;

import io.github.eutro.wasm2j.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.passes.meta.InferTypes;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.ssa.JClass;
import io.github.eutro.wasm2j.ssa.JClass.JavaMethod;
import io.github.eutro.wasm2j.ssa.Var;
import io.github.eutro.wasm2j.util.Lazy;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

/**
 * A collection of {@link Ext}s that exist in Java IR.
 */
public class JavaExts {
    /**
     * Attached to a {@link JavaMethod}. Its IR implementation, if any.
     */
    public static final Ext<Lazy<Function>> METHOD_IMPL = Ext.create(Lazy.class, "METHOD_IMPL");
    /**
     * Attached to a {@link JavaMethod}. Its native (Java bytecode) implementation, if any.
     */
    public static final Ext<MethodNode> METHOD_NATIVE_IMPL = Ext.create(MethodNode.class, "METHOD_NATIVE_IMPL");

    /**
     * Attached to a {@link Var}, computed by {@link InferTypes.Java}. Its Java type.
     */
    public static final Ext<Type> TYPE = Ext.create(Type.class, "TYPE");
    /**
     * Attached to a {@link Function}. The {@link JavaMethod} it is the implementation for.
     */
    public static final Ext<JavaMethod> FUNCTION_METHOD = Ext.create(JavaMethod.class, "FUNCTION_METHOD");

    /**
     * Attached to a {@link JClass}.
     * The map of not-{@link IntrinsicImpl#inline inlined} intrinsics that have been added to the class.
     */
    public static final Ext<Map<IntrinsicImpl, JavaMethod>> ATTACHED_INTRINSICS = Ext.create(Map.class, "ATTACHED_INTRINSICS");

    /**
     * The type of an {@link org.objectweb.asm.Opcodes#ACONST_NULL ACONST_NULL} instruction.
     */
    public static Type BOTTOM_TYPE = Type.getType(Void.class);
}

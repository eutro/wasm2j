package io.github.eutro.wasm2j.ext;

import io.github.eutro.wasm2j.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.ssa.Function;
import io.github.eutro.wasm2j.ssa.JClass;
import io.github.eutro.wasm2j.util.Lazy;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

public class JavaExts {
    public static final Ext<Lazy<Function>> METHOD_IMPL = Ext.create(Lazy.class, "METHOD_IMPL");
    public static final Ext<MethodNode> METHOD_NATIVE_IMPL = Ext.create(MethodNode.class, "METHOD_NATIVE_IMPL");
    public static final Ext<Type> TYPE = Ext.create(Type.class, "TYPE");
    public static final Ext<JClass.JavaMethod> FUNCTION_METHOD = Ext.create(JClass.JavaMethod.class, "FUNCTION_METHOD");
    public static final Ext<JClass> FUNCTION_OWNER = Ext.create(JClass.class, "FUNCTION_OWNER");

    public static final Ext<Map<IntrinsicImpl, JClass.JavaMethod>> ATTACHED_INTRINSICS = Ext.create(Map.class, "ATTACHED_INTRINSICS");

    public static Type BOTTOM_TYPE = Type.getType(Void.class);
}

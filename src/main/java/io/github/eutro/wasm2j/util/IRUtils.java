package io.github.eutro.wasm2j.util;

import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Insn;
import io.github.eutro.wasm2j.ssa.Var;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnNode;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

import static io.github.eutro.wasm2j.ext.CommonExts.markPure;

public class IRUtils {
    public static final JavaExts.JavaClass BYTE_BUFFER_CLASS = new JavaExts.JavaClass(Type.getInternalName(ByteBuffer.class));
    public static final JavaExts.JavaClass METHOD_HANDLE_CLASS = new JavaExts.JavaClass(Type.getInternalName(MethodHandle.class));
    public static final JavaExts.JavaClass MTY_CLASS = new JavaExts.JavaClass(Type.getInternalName(MethodType.class));
    public static final JavaExts.JavaMethod MTY_METHOD_TYPE = new JavaExts.JavaMethod(
            MTY_CLASS,
            "methodType",
            Type.getMethodType(
                    Type.getType(MethodType.class),
                    Type.getType(Class.class),
                    Type.getType(Class[].class)
            ).getDescriptor(),
            JavaExts.JavaMethod.Kind.STATIC
    );

    public static Var getThis(IRBuilder ib) {
        return ib.insert(JavaOps.THIS.insn(), "this");
    }

    public static Insn loadClass(Type ty) {
        switch (ty.getSort()) {
            case Type.OBJECT:
            case Type.ARRAY:
                return CommonOps.constant(ty);
            default: {
                Class<?> boxedTy;
                switch (ty.getSort()) {
                    // @formatter:off
                    case Type.VOID: boxedTy = Void.class; break;
                    case Type.BOOLEAN: boxedTy = Boolean.class; break;
                    case Type.CHAR: boxedTy = Character.class; break;
                    case Type.BYTE: boxedTy = Byte.class; break;
                    case Type.SHORT: boxedTy = Short.class; break;
                    case Type.INT: boxedTy = Integer.class; break;
                    case Type.FLOAT: boxedTy = Float.class; break;
                    case Type.LONG: boxedTy = Long.class; break;
                    case Type.DOUBLE: boxedTy = Double.class; break;
                    // @formatter:on
                    default:
                        throw new IllegalArgumentException();
                }
                return JavaOps.GET_FIELD.create(new JavaExts.JavaField(
                        new JavaExts.JavaClass(Type.getInternalName(boxedTy)),
                        "TYPE",
                        Type.getDescriptor(Class.class),
                        true
                )).insn();
            }
        }
    }

    public static Var getAddr(IRBuilder ib, WasmOps.WithMemArg<?> wmArg, Var ptr) {
        return wmArg.offset == 0
                ? ptr
                : ib.insert(markPure(JavaOps.insns(new InsnNode(Opcodes.IADD)))
                        .insn(ptr,
                                ib.insert(CommonOps.constant(wmArg.offset),
                                        "offset")),
                "addr");
    }
}

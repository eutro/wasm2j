package io.github.eutro.wasm2j.util;

import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Insn;
import io.github.eutro.wasm2j.ssa.Var;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;

public class IRUtils {
    public static final JavaExts.JavaClass BYTE_BUFFER_CLASS = new JavaExts.JavaClass(Type.getInternalName(ByteBuffer.class));
    public static final JavaExts.JavaClass METHOD_HANDLE_CLASS = new JavaExts.JavaClass(Type.getInternalName(MethodHandle.class));

    public static Var getThis(IRBuilder ib) {
        return ib.insert(JavaOps.THIS.insn(), "this");
    }

    public static Insn loadClass(Type ty) {
        switch (ty.getSort()) {
            case Type.OBJECT:
            case Type.ARRAY:
                return CommonOps.CONST.create(ty).insn();
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
}

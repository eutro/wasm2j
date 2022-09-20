package io.github.eutro.wasm2j.util;

import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ssa.IRBuilder;
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
}

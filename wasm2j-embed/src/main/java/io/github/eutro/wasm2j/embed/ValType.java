package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.Opcodes;

import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;

public enum ValType {
    I32(int.class, Opcodes.I32),
    I64(long.class, Opcodes.I64),
    F32(float.class, Opcodes.F32),
    F64(double.class, Opcodes.F64),
    FUNCREF(MethodHandle.class, Opcodes.FUNCREF),
    EXTERNREF(Object.class, Opcodes.EXTERNREF),
    V128(ByteBuffer.class, Opcodes.V128),
    ;

    private final Class<?> type;
    private final byte opcode;

    ValType(Class<?> type, byte opcode) {
        this.type = type;
        this.opcode = opcode;
    }

    public static ValType forClass(Class<?> javaClass) {
        for (ValType value : values()) {
            if (value.type == javaClass) {
                return value;
            }
        }
        return null;
    }

    public Class<?> getType() {
        return type;
    }

    public byte getOpcode() {
        return opcode;
    }
}

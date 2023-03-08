package io.github.eutro.wasm2j.support;

import io.github.eutro.jwasm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.Locale;

/**
 * An enum that represents a WebAssembly type, and gives its canonical mapping to a Java class.
 *
 * <blockquote>
 *   <table>
 *     <caption>Java classes of current WebAssembly valtypes</caption>
 *     <tr><td>WebAssembly ValType</td><td>Java Class</td><td>Notes</td></tr>
 *     <tr><td>i32</td><td>{@link Integer#TYPE int}</td></tr>
 *     <tr><td>i64</td><td>{@link Long#TYPE long}</td></tr>
 *     <tr><td>f32</td><td>{@link Float#TYPE float}</td></tr>
 *     <tr><td>f64</td><td>{@link Double#TYPE double}</td></tr>
 *     <tr><td>funcref</td><td>{@link MethodHandle}</td></tr>
 *     <tr><td>externref</td><td>{@link Object}</td></tr>
 *     <tr><td>v128</td><td>{@link ByteBuffer}</td><td>{@link java.nio.ByteOrder#LITTLE_ENDIAN Little endian} byte order</td></tr>
 *   </table>
 * </blockquote>
 */
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

    public static ValType fromOpcode(byte opcode) {
        switch (opcode) {
            case Opcodes.I32:
                return I32;
            case Opcodes.I64:
                return I64;
            case Opcodes.F32:
                return F32;
            case Opcodes.F64:
                return F64;
            case Opcodes.FUNCREF:
                return FUNCREF;
            case Opcodes.EXTERNREF:
                return EXTERNREF;
            case Opcodes.V128:
                return V128;
            default:
                throw new IllegalArgumentException();
        }
    }

    public Class<?> getType() {
        return type;
    }

    public Type getAsmType() {
        return Type.getType(getType());
    }

    public byte getOpcode() {
        return opcode;
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}

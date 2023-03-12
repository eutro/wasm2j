package io.github.eutro.wasm2j.api.types;

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
    /**
     * A 32-bit integer, represented as a Java int.
     */
    I32(int.class, Opcodes.I32),
    /**
     * A 64-bit integer, represented as a Java long.
     */
    I64(long.class, Opcodes.I64),
    /**
     * A 32-bit floating-point value, represented as a Java float.
     */
    F32(float.class, Opcodes.F32),
    /**
     * A 64-bit floating-point value, represented as a Java double.
     */
    F64(double.class, Opcodes.F64),
    /**
     * A function reference, represented as a Java {@link MethodHandle}.
     */
    FUNCREF(MethodHandle.class, Opcodes.FUNCREF),
    /**
     * An external reference, represented as a Java {@link Object}.
     */
    EXTERNREF(Object.class, Opcodes.EXTERNREF),
    /**
     * A 128-bit vector, represented as a Java {@link ByteBuffer}.
     */
    V128(ByteBuffer.class, Opcodes.V128),
    ;

    private final Class<?> type;
    private final byte opcode;

    ValType(Class<?> type, byte opcode) {
        this.type = type;
        this.opcode = opcode;
    }

    /**
     * Get the {@link ValType} for a given WebAssembly type code, such as {@link Opcodes#I32}.
     *
     * @param opcode The opcode.
     * @return The value type.
     */
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

    /**
     * Get the Java class for this type.
     *
     * @return The Java class.
     */
    public Class<?> getType() {
        return type;
    }

    /**
     * Get the ASM type for this type.
     *
     * @return The ASM type.
     */
    public Type getAsmType() {
        return Type.getType(getType());
    }

    /**
     * Get the opcode byte for this type.
     *
     * @return The WebAssembly opcode byte.
     */
    public byte getOpcode() {
        return opcode;
    }

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}

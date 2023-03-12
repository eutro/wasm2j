package io.github.eutro.wasm2j.core.intrinsics;

import io.github.eutro.jwasm.Opcodes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation that defines which WebAssembly instructions a method is an intrinsic implementation for.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Intrinsic {
    /**
     * Returns the first byte of the opcode.
     *
     * @return The first byte of the opcode.
     */
    byte value() default Opcodes.INSN_PREFIX;

    /**
     * Returns the tail of a multibyte opcode.
     *
     * @return The tail of a multibyte opcode.
     */
    int iOp() default -1;

    /**
     * Returns whether the implementation of the intrinsic should be inlined into its caller.
     *
     * @return Whether the implementation of the intrinsic should be inlined into its caller.
     */
    boolean inline() default true;
}

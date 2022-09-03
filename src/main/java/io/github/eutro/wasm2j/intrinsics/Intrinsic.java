package io.github.eutro.wasm2j.intrinsics;

import io.github.eutro.jwasm.Opcodes;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.CLASS)
public @interface Intrinsic {
    byte value() default Opcodes.INSN_PREFIX;

    int iOp() default 0;

    boolean inline() default true;
}

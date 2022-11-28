package io.github.eutro.wasm2j.intrinsics;

import io.github.eutro.jwasm.Opcodes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Intrinsic {
    byte value() default Opcodes.INSN_PREFIX;

    int iOp() default -1;

    boolean inline() default true;
}

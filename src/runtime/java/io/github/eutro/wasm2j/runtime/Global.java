package io.github.eutro.wasm2j.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation on a field that denotes that the field represents a global variable of the module.
 * <p>
 * This can be obtained at runtime using reflection, if this class is available at runtime.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Global {
}

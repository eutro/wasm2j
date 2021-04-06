package io.github.eutro.wasm2j.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation on a field that denotes that the field represents a linear memory of the module,
 * including its limits.
 * <p>
 * This can be obtained at runtime using reflection, if this class is available at runtime.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface LinearMemory {
    /**
     * @return The minimum value of the linear memory.
     */
    int min();

    /**
     * @return The maximum value of the linear memory.
     */
    int max() default -1;
}

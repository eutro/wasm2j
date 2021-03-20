package io.github.eutro.wasm2j.runtime;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The containing annotation for {@link CustomSection}.
 *
 * @see java.lang.annotation.Repeatable
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CustomSections {
    /**
     * @return An array of {@link CustomSection}s.
     */
    CustomSection[] value();
}

package io.github.eutro.wasm2j.runtime;

import java.lang.annotation.*;

/**
 * An annotation on a class that denotes custom data from the WebAssembly module it was decoded from.
 * <p>
 * This can be obtained at runtime using reflection, if this class is available at runtime.
 */
@Repeatable(CustomSections.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface CustomSection {
    /**
     * @return The name of the custom section.
     */
    String name();

    /**
     * @return The contents of the custom section.
     */
    byte[] data();
}

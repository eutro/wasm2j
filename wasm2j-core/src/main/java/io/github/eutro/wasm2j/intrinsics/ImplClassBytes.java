package io.github.eutro.wasm2j.intrinsics;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;

/**
 * A class for finding the bytes a Java class.
 */
public class ImplClassBytes {
    /**
     * Get a class reader that reads the given class.
     *
     * @param clazz The class.
     * @return The class reader.
     */
    public static ClassReader getClassReaderFor(Class<?> clazz) {
        String path = "/" + clazz.getName().replace('.', '/') + ".class";
        try (InputStream implStream = clazz.getResourceAsStream(path)) {
            if (implStream == null) {
                throw new RuntimeException("Could not get input stream for " + clazz);
            }
            return new ClassReader(implStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

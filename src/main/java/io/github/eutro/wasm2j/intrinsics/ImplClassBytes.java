package io.github.eutro.wasm2j.intrinsics;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;

public class ImplClassBytes {
    public static ClassReader getClassReader() {
        return getClassReaderFor(Impl.class);
    }

    public static ClassReader getClassReaderFor(Class<?> clazz) {
        String path = "/" + clazz.getName().replace('.', '/') + ".class";
        try (InputStream implStream = clazz.getResourceAsStream(path)) {
            if (implStream == null) throw new RuntimeException("Could not get Impl class stream.");
            return new ClassReader(implStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

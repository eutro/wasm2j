package io.github.eutro.wasm2j.embed;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodTooLargeException;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.nio.file.Files;

public class Store {
    private final StoreClassLoader loader = new StoreClassLoader();

    Class<?> defineClass(ClassNode node, File debugOutput) {
        return loader.defineClass(node, debugOutput);
    }

    private static class StoreClassLoader extends ClassLoader {
        public Class<?> defineClass(ClassNode node, File debugOutput) {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            node.accept(cw);
            byte[] bytes;
            try {
                bytes = cw.toByteArray();
            } catch (MethodTooLargeException e) {
                throw new ModuleRefusedException(e);
            }
            if (debugOutput != null) {
                boolean ignored = debugOutput.mkdirs();
                try {
                    Files.write(debugOutput.toPath()
                            .resolve(node.name.substring(
                                    node.name.lastIndexOf('/') + 1) + ".class"
                            ),
                            bytes);
                } catch (Exception ignored1) {
                }
            }
            return defineClass(node.name.replace('/', '.'), bytes, 0, bytes.length);
        }
    }
}

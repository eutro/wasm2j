package io.github.eutro.wasm2j.api.bits;

import io.github.eutro.wasm2j.api.events.EmitClassEvent;
import io.github.eutro.wasm2j.api.events.EventSupplier;
import org.objectweb.asm.ClassWriter;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A bit which outputs emitted classes to the given directory.
 *
 * @param <T> The type on which this listens to events.
 */
public class OutputsToDirectory<T extends EventSupplier<? super EmitClassEvent>>
        implements Bit<T, Void> {

    private final Path directory;

    /**
     * Construct a {@link OutputsToDirectory} for outputting to the given directory.
     *
     * @param directory The directory to output class files to.
     */
    public OutputsToDirectory(Path directory) {
        this.directory = directory;
    }

    @Override
    public Void addTo(T cc) {
        cc.listen(EmitClassEvent.class, evt -> {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            evt.classNode.accept(cw);
            byte[] bytes = cw.toByteArray();

            String[] path = evt.classNode.name.split("/");
            Path dir = directory;
            for (int i = 0; i < path.length - 1; i++) {
                dir = dir.resolve(path[i]);
            }
            try {
                Files.createDirectories(dir);
                Path classFile = dir.resolve(path[path.length - 1] + ".class");
                Files.write(classFile, bytes);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return null;
    }
}

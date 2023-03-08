package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.ByteInputStream;
import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.ValidationException;
import io.github.eutro.jwasm.sexp.Parser;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.bits.Bit;
import io.github.eutro.wasm2j.events.*;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static io.github.eutro.jwasm.sexp.Reader.readAll;

public class WasmCompiler extends EventSupplier<CompilerEvent> {
    public void submitBinary(InputStream stream) throws IOException {
        submitBinary(new ByteInputStream.InputStreamByteInputStream(stream));
    }

    public void submitBinary(ByteBuffer buffer) {
        submitBinary(new ByteInputStream.ByteBufferByteInputStream(buffer));
    }

    public <E extends Exception> void submitBinary(ByteInputStream<E> stream) throws E {
        ModuleNode node = new ModuleNode();
        new ModuleReader<>(() -> stream).accept(node);
        submitNode(node);
    }

    public void submitText(String source) {
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        submitText(new ByteInputStream.ByteBufferByteInputStream(ByteBuffer.wrap(bytes)));
    }

    public <E extends Exception> void submitText(ByteInputStream<E> stream) throws E {
        List<Object> exprs = readAll(stream);
        if (exprs.size() != 1) {
            throw new ValidationException("Expected exactly one expression in source.");
        }
        ModuleNode node = Parser.parseModule(exprs.get(0));
        submitNode(node);
    }

    public void submitNode(ModuleNode node) {
        newCompilation(node).submit();
    }

    @NotNull
    private ModuleCompilation newCompilation(ModuleNode node) {
        return dispatch(NewModuleCompilationEvent.class, new NewModuleCompilationEvent(new ModuleCompilation(node)))
                .compilation;
    }

    public EventDispatcher<ModuleCompileEvent> lift() {
        return new EventDispatcher<ModuleCompileEvent>() {
            @Override
            public <T extends ModuleCompileEvent> void listen(Class<T> eventClass, @NotNull Consumer<T> listener) {
                WasmCompiler.this.listen(NewModuleCompilationEvent.class, evt -> {
                    evt.compilation.listen(eventClass, listener);
                });
            }
        };
    }

    public Consumer<String> setNextNames() {
        AtomicReference<String> name = new AtomicReference<>("org/example/FIXME");
        lift().listen(ModifyConventionsEvent.class, evt -> evt.conventionBuilder.setNameSupplier(name::get));
        return name::set;
    }

    public BlockingQueue<ClassNode> outputsAsQueue() {
        BlockingQueue<ClassNode> queue = new LinkedBlockingQueue<>();
        lift().listen(EmitClassEvent.class, evt -> queue.add(evt.classNode));
        return queue;
    }

    public <T> T add(Bit<T> bit) {
        return bit.addTo(this);
    }

    public void outputsToDirectory(Path directory) {
        lift().listen(EmitClassEvent.class, evt -> {
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
    }
}

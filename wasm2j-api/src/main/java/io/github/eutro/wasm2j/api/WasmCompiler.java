package io.github.eutro.wasm2j.api;

import io.github.eutro.jwasm.ByteInputStream;
import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.ValidationException;
import io.github.eutro.jwasm.sexp.WatParser;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.api.bits.Bit;
import io.github.eutro.wasm2j.api.events.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import static io.github.eutro.jwasm.sexp.WatReader.readAll;

public class WasmCompiler extends EventSupplier<CompilerEvent> {
    @Contract(pure = true)
    public ModuleCompilation submitBinary(InputStream stream) throws IOException {
        return submitBinary(new ByteInputStream.InputStreamByteInputStream(stream));
    }

    @Contract(pure = true)
    public ModuleCompilation submitBinary(ByteBuffer buffer) {
        return submitBinary(new ByteInputStream.ByteBufferByteInputStream(buffer));
    }

    @Contract(pure = true)
    public <E extends Exception> ModuleCompilation submitBinary(ByteInputStream<E> stream) throws E {
        ModuleNode node = new ModuleNode();
        new ModuleReader<>(() -> stream).accept(node);
        return submitNode(node);
    }

    @Contract(pure = true)
    public ModuleCompilation submitText(String source) {
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        return submitText(new ByteInputStream.ByteBufferByteInputStream(ByteBuffer.wrap(bytes)));
    }

    @Contract(pure = true)
    public <E extends Exception> ModuleCompilation submitText(ByteInputStream<E> stream) throws E {
        List<Object> exprs = readAll(stream);
        if (exprs.size() != 1) {
            throw new ValidationException("Expected exactly one expression in source.");
        }
        ModuleNode node = WatParser.DEFAULT.parseModule(exprs.get(0));
        return submitNode(node);
    }

    @Contract(pure = true)
    public ModuleCompilation submitNode(ModuleNode node) {
        return newCompilation(node);
    }

    // it's not, but show a warning if the result is unused
    @Contract(pure = true)
    @NotNull
    private ModuleCompilation newCompilation(ModuleNode node) {
        return new ModuleCompilation(this, node);
    }

    public EventDispatcher<ModuleCompileEvent> lift() {
        return new EventDispatcher<ModuleCompileEvent>() {
            @Override
            public <T extends ModuleCompileEvent> void listen(Class<T> eventClass, @NotNull Consumer<T> listener) {
                WasmCompiler.this.listen(RunModuleCompilationEvent.class, evt ->
                        evt.compilation.listen(eventClass, listener));
            }
        };
    }

    public BlockingQueue<ClassNode> outputsAsQueue() {
        BlockingQueue<ClassNode> queue = new LinkedBlockingQueue<>();
        lift().listen(EmitClassEvent.class, evt -> queue.add(evt.classNode));
        return queue;
    }

    public <T> T add(Bit<? super WasmCompiler, T> bit) {
        return bit.addTo(this);
    }
}

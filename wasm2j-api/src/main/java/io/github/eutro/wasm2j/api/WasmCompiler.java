package io.github.eutro.wasm2j.api;

import io.github.eutro.jwasm.ByteInputStream;
import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.ValidationException;
import io.github.eutro.jwasm.sexp.WatParser;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.jwasm.tree.analysis.ModuleValidator;
import io.github.eutro.wasm2j.api.bits.Bit;
import io.github.eutro.wasm2j.api.events.*;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

import static io.github.eutro.jwasm.sexp.WatReader.readAll;

/**
 * A compiler from WebAssembly to Java bytecode.
 * <p>
 * Configuration is driven by events. See {@link io.github.eutro.wasm2j.api.events} for details.
 *
 * @see ModuleCompilation
 * @see CompilerEvent
 */
public class WasmCompiler extends EventSupplier<CompilerEvent> {
    /**
     * Submit an input stream of a WebAssembly binary for compilation.
     *
     * @param stream The stream.
     * @return The un-started compilation of the module.
     * @throws IOException If an error occurs reading from the stream.
     * @see ModuleCompilation#run()
     */
    @Contract(pure = true)
    public ModuleCompilation submitBinary(InputStream stream) throws IOException {
        return submitBinary(new ByteInputStream.InputStreamByteInputStream(stream));
    }

    /**
     * Submit a byte buffer of a WebAssembly binary for compilation.
     *
     * @param buffer The buffer.
     * @return The un-started compilation of the module.
     * @see ModuleCompilation#run()
     */
    @Contract(pure = true)
    public ModuleCompilation submitBinary(ByteBuffer buffer) {
        return submitBinary(new ByteInputStream.ByteBufferByteInputStream(buffer));
    }

    /**
     * Submit an input stream of a WebAssembly binary for compilation.
     *
     * @param stream The stream.
     * @param <E>    The type of error the stream may throw.
     * @return The un-started compilation of the module.
     * @throws E If an error occurs reading from the stream.
     * @see ModuleCompilation#run()
     */
    @Contract(pure = true)
    public <E extends Exception> ModuleCompilation submitBinary(ByteInputStream<E> stream) throws E {
        ModuleNode node = new ModuleNode();
        new ModuleReader<>(() -> stream).accept(new ModuleValidator(node));
        return newCompilation(node);
    }

    /**
     * Submit a source string of a WebAssembly text file for compilation.
     *
     * @param source The source string.
     * @return The un-started compilation of the module.
     * @see ModuleCompilation#run()
     */
    @Contract(pure = true)
    public ModuleCompilation submitText(String source) {
        byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        return submitText(new ByteInputStream.ByteBufferByteInputStream(ByteBuffer.wrap(bytes)));
    }

    /**
     * Submit a source input stream of a WebAssembly text file for compilation.
     *
     * @param stream The source stream.
     * @param <E>    The type of error the stream can throw.
     * @return The un-started compilation of the module.
     * @throws E If an error occurs reading the stream.
     * @see ModuleCompilation#run()
     */
    @Contract(pure = true)
    public <E extends Exception> ModuleCompilation submitText(ByteInputStream<E> stream) throws E {
        List<Object> exprs = readAll(stream);
        if (exprs.size() != 1) {
            throw new ValidationException("Expected exactly one expression in source.");
        }
        ModuleNode node = WatParser.DEFAULT.parseModule(exprs.get(0));
        return submitNode(node);
    }

    /**
     * Submit an already parsed WebAssembly module for compilation.
     *
     * @param node The parsed module node.
     * @return The un-started compilation of the module.
     * @see ModuleCompilation#run()
     */
    @Contract(pure = true)
    public ModuleCompilation submitNode(ModuleNode node) {
        node.accept(new ModuleValidator());
        return newCompilation(node);
    }

    // it's not, but show a warning if the result is unused
    @Contract(pure = true)
    @NotNull
    private ModuleCompilation newCompilation(ModuleNode node) {
        return new ModuleCompilation(this, node);
    }

    /**
     * Get an event dispatcher that allows for listening to the events
     * for every compilation that goes through this compiler.
     *
     * @return The dispatcher.
     */
    public EventDispatcher<ModuleCompileEvent> lift() {
        return new EventDispatcher<ModuleCompileEvent>() {
            @Override
            public <T extends ModuleCompileEvent> void listen(Class<T> eventClass, @NotNull Consumer<T> listener) {
                WasmCompiler.this.listen(RunModuleCompilationEvent.class, evt ->
                        evt.compilation.listen(eventClass, listener));
            }
        };
    }

    /**
     * Add a {@link Bit bit} to this compiler.
     *
     * @param bit The bit to add.
     * @param <T> The type of the result.
     * @return The result of adding the bit.
     */
    public <T> T add(Bit<? super WasmCompiler, T> bit) {
        return bit.addTo(this);
    }
}

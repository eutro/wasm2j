package io.github.eutro.wasm2j.api.bits;

import io.github.eutro.jwasm.ByteInputStream;
import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.sexp.WatParser;
import io.github.eutro.jwasm.sexp.WatReader;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.api.ModuleCompilation;
import io.github.eutro.wasm2j.api.WasmCompiler;
import org.jetbrains.annotations.Contract;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * A class for detecting the format of a WebAssembly file (text or binary).
 *
 * @see Format#detect(ByteBuffer)
 */
public class FormatDetector {
    /**
     * A {@link Bit} which can be attached to a compiler.
     */
    public static Bit<WasmCompiler, FormatDetector> BIT = FormatDetector::new;
    private final WasmCompiler cc;

    /**
     * Construct a format detector which will submit files to the given compiler.
     *
     * @param cc The compiler.
     */
    public FormatDetector(WasmCompiler cc) {
        this.cc = cc;
    }

    /**
     * Submit a file to the compiler, detecting its type.
     *
     * @param path The path to the file.
     * @return The compilation. Must be used.
     * @throws IOException If reading the file fails.
     */
    @Contract(pure = true)
    public ModuleCompilation submitFile(Path path) throws IOException {
        try (FileChannel fc = FileChannel.open(path)) {
            MappedByteBuffer buf = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            return submitBuf(buf);
        }
    }

    /**
     * Submit a byte buffer to the compiler, detecting its type.
     *
     * @param buf The bytes.
     * @return The compilation. Must be used.
     */
    @Contract(pure = true)
    public ModuleCompilation submitBuf(ByteBuffer buf) {
        return cc.submitNode(moduleFromBuf(buf));
    }

    private static ModuleNode moduleFromBuf(ByteBuffer buf) {
        ModuleNode module;
        switch (Format.detect(buf)) {
            case TEXT: {
                List<Object> objs = WatReader.readAll(new ByteInputStream.ByteBufferByteInputStream(buf));
                if (objs.size() != 1) {
                    throw new IllegalArgumentException("wrong number of s-exprs in file");
                }
                module = WatParser.DEFAULT.parseModule(objs.get(0));
                break;
            }
            case BINARY: {
                new ModuleReader<>(() -> new ByteInputStream.ByteBufferByteInputStream(buf))
                        .accept(module = new ModuleNode());
                break;
            }
            case UNKNOWN:
                throw new IllegalArgumentException("unrecognised Wasm module format");
            default:
                throw new IllegalStateException();
        }
        return module;
    }

    /**
     * The format of a WebAssembly file, either text, binary, or unknown.
     */
    public enum Format {
        /**
         * The WebAssembly
         * <a href="https://webassembly.github.io/spec/core/binary/index.html">binary format</a>.
         */
        BINARY,
        /**
         * The WebAssembly
         * <a href="https://webassembly.github.io/spec/core/text/index.html">text format</a>.
         */
        TEXT,
        /**
         * The format could not be detected.
         */
        UNKNOWN,
        ;

        /**
         * Try to detect the format of the bytes in a buffer. Only the first few bytes are read.
         * <p>
         * This will first try to check if the magic header "\0asm" is detected, and will
         * report {@link #BINARY} if it is.
         * <p>
         * Otherwise, it will try to see if the first few bytes of the buffer are valid UTF-8. If
         * they are, {@link #TEXT} will be reported. Note that ".wat" files, the recommended
         * for WebAssembly, are, <a href="https://webassembly.github.io/spec/core/text/conventions.html">
         * according to the specification, assumed to be encoded in UTF-8</a>.
         * <p>
         * If both of these fail, {@link #UNKNOWN} is reported.
         *
         * @param buf The buffer.
         * @return The detected format.
         */
        public static Format detect(ByteBuffer buf) {
            if (buf.capacity() < 4) return UNKNOWN;
            // check for magic header
            {
                byte[] maybeMagic = new byte[4];
                buf.slice().get(maybeMagic);
                if (Arrays.equals(
                        new byte[]{'\0', 'a', 's', 'm'},
                        maybeMagic
                )) {
                    return BINARY;
                }
            }
            // check if the first few bytes are valid UTF 8
            {
                CharsetDecoder dec = StandardCharsets.UTF_8.newDecoder();
                int toScan = Math.min(128, buf.capacity());
                CharBuffer cbuf = CharBuffer.allocate(toScan);
                ByteBuffer sliced = buf.slice();
                sliced.limit(toScan);
                CoderResult res = dec.decode(sliced, cbuf, false);
                if (!res.isError()) {
                    return TEXT;
                }
            }
            // definitely not a valid module
            return UNKNOWN;
        }
    }
}

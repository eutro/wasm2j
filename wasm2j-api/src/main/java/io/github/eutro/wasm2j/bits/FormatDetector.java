package io.github.eutro.wasm2j.bits;

import io.github.eutro.jwasm.ByteInputStream;
import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.sexp.WatParser;
import io.github.eutro.jwasm.sexp.WatReader;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.ModuleCompilation;
import io.github.eutro.wasm2j.WasmCompiler;
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

public class FormatDetector {
    public static Bit<WasmCompiler, FormatDetector> BIT = FormatDetector::new;
    private final WasmCompiler cc;

    public FormatDetector(WasmCompiler cc) {
        this.cc = cc;
    }


    @Contract(pure = true)
    public ModuleCompilation submitFile(Path path) throws IOException {
        try (FileChannel fc = FileChannel.open(path)) {
            MappedByteBuffer buf = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            return submitBuf(buf);
        }
    }

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

    public enum Format {
        TEXT,
        BINARY,
        UNKNOWN,
        ;

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
                CoderResult res = dec.decode(buf.slice().limit(toScan), cbuf, false);
                if (!res.isError()) {
                    return TEXT;
                }
            }
            // definitely not a valid module
            return UNKNOWN;
        }
    }
}

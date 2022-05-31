package io.github.eutro.wasm2j.test;

import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.wasm2j.ModuleAdapter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

public class ModuleAdapterTest {

    public static final Path WASMOUT = Paths.get("build", "wasmout", "jwasm");

    @BeforeAll
    static void beforeAll() throws IOException {
        Files.createDirectories(WASMOUT);
    }

    Object adapt(String name, Function<String, ModuleAdapter> maSupplier) throws Throwable {
        String className = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "Bg";
        ModuleAdapter ma = maSupplier.apply("jwasm/" + className);
        try (InputStream is = ModuleAdapterTest.class.getResourceAsStream("/" + name + "_bg.wasm")) {
            ModuleReader.fromInputStream(is).accept(ma);
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        ma.accept(new CheckClassAdapter(cw));
        byte[] bytes = cw.toByteArray();
        Files.write(WASMOUT.resolve(className + ".class"), bytes);
        Object[] out = new Object[1];
        new ClassLoader() {
            {
                Class<?> clazz = defineClass("jwasm." + className, bytes, 0, bytes.length);
                out[0] = clazz.getConstructor().newInstance();
            }
        };
        return out[0];
    }

    @Test
    void simple() throws Throwable {
        adapt("simple", ModuleAdapter::new);
    }

    @Test
    void unsimple() throws Throwable {
        Object unsimple = adapt("unsimple", ModuleAdapter::new);
        MethodHandle div = MethodHandles.insertArguments(MethodHandles.lookup()
                        .unreflect(unsimple.getClass().getMethod("div", int.class, int.class)),
                0,
                unsimple);
        MethodHandle divU = MethodHandles.insertArguments(MethodHandles.lookup()
                        .unreflect(unsimple.getClass().getMethod("div_u", long.class, long.class)),
                0,
                unsimple);
        MethodHandle assembleLongs = MethodHandles.insertArguments(MethodHandles.lookup()
                        .unreflect(unsimple.getClass().getMethod("assemble_longs", long.class)),
                0,
                unsimple);
        assertEquals(50, (int) div.invokeExact(100, 2));
        assertEquals(Long.MAX_VALUE, (long) divU.invokeExact(-1L, 2L));
        assertThrows(AssertionError.class, () -> {
            @SuppressWarnings("unused")
            int ignored = (int) div.invokeExact(1, 0);
        });
        assertDoesNotThrow(() -> (long) assembleLongs.invokeExact(1L));
    }
}

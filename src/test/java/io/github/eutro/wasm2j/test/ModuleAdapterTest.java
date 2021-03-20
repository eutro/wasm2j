package io.github.eutro.wasm2j.test;

import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.wasm2j.InteropModuleAdapter;
import io.github.eutro.wasm2j.ModuleAdapter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.util.CheckClassAdapter;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

public class ModuleAdapterTest {

    public static final Path WASMOUT = Paths.get("build", "wasmout", "jwasm");

    @BeforeAll
    static void beforeAll() throws IOException {
        Files.createDirectories(WASMOUT);
    }

    Object adapt(String name, ModuleAdapter ma) throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        try (InputStream is = ModuleAdapterTest.class.getResourceAsStream("/" + name + "_bg.wasm")) {
            ModuleReader.fromInputStream(is).accept(ma);
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        String className = name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + "Bg";
        ma.toJava("jwasm/" + className).accept(new CheckClassAdapter(cw));
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
    void simple_bg() throws IOException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        adapt("simple", new ModuleAdapter());
    }

    @Test
    void unsimple() throws Throwable {
        Object unsimple = adapt("unsimple", new ModuleAdapter());
        MethodHandle div = MethodHandles.insertArguments(MethodHandles.lookup()
                .unreflect(unsimple.getClass().getMethod("div", int.class, int.class)),
                0,
                unsimple);
        MethodHandle divU = MethodHandles.insertArguments(MethodHandles.lookup()
                        .unreflect(unsimple.getClass().getMethod("div_u", long.class, long.class)),
                0,
                unsimple);
        assertEquals(50, (int) div.invokeExact(100, 2));
        assertEquals(Long.MAX_VALUE, (long) divU.invokeExact(-1L, 2L));
        assertThrows(AssertionError.class, () -> {
            @SuppressWarnings("unused")
            int ignored = (int) div.invokeExact(1, 0);
        });
    }

    @Test
    void interop() throws Throwable {
        Object interop = adapt("interop", new InteropModuleAdapter());
        Class<?> clazz = interop.getClass();
        assertEquals(1D / Math.sin(100D),
                clazz.getMethod("csc", double.class).invoke(interop, 100D));
        assertEquals(1D / Math.cos(100D),
                clazz.getMethod("sec", double.class).invoke(interop, 100D));
        assertEquals(1D / Math.tan(100D),
                clazz.getMethod("cot", double.class).invoke(interop, 100D));
        assertEquals(1D / Math.tan(100D),
                clazz.getMethod("cot", double.class).invoke(interop, 100D));

        Method func = clazz.getMethod("func", int.class);
        Method invoke = clazz.getMethod("invoke", int.class, double.class);
        for (int i = 0; i < 6; i++) {
            int f = (int) func.invoke(interop, i);
            invoke.invoke(interop, f, 1);
        }
    }
}

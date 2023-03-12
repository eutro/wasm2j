package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.embed.internal.Utils;
import io.github.eutro.wasm2j.embed.internal.WasmConvertPass;
import io.github.eutro.wasm2j.core.passes.IRPass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodTooLargeException;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.nio.file.Files;

/**
 * Formally, in the WebAssembly specification, the store contains (owns) all the
 * values (functions, memories, tables) of a set of modules. Since in pure Java
 * all of these values are managed by the garbage collector, the store does nothing
 * more than handle the runtime instantiation of compiled modules.
 * <p>
 * In the current implementation, runtime class loading is done as follows:
 * <ul>
 *     <li>By a subclass of {@link ClassLoader}, owned by the store object.
 *     The classes of instantiated modules will never be freed while the class loader is reachable,
 *     either from this {@link Store} or from a class loaded by the class loader. (Java 8-14)</li>
 *     <li>Using {@code defineHiddenClass} of {@link MethodHandles.Lookup}.
 *     The classes of instantiated modules are hidden and may be garbage collected when the module
 *     instances become unreachable. (Java 15+)</li>
 * </ul>
 * <p>
 * The former may be forced on a given store by calling {@link #forceClassLoaderDefiner()}.
 */
public final class Store {
    final IRPass<ModuleNode, ClassNode> PASS = WasmConvertPass.getPass();
    @NotNull
    private ClassDefiner definer = getClassDefiner();

    private File debugOutput;

    private Store() {
    }

    @Embedding("store_init")
    public static Store init() {
        return new Store();
    }

    public void forceClassLoaderDefiner() {
        if (definer instanceof StoreClassLoader) return;
        definer = new StoreClassLoader();
    }

    public void setDebugOutput(@Nullable File debugOutput) {
        this.debugOutput = debugOutput;
    }

    Class<?> defineClass(ClassNode node) {
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
        return definer.defineClass(node.name.replace('/', '.'), bytes);
    }

    private static ClassDefiner getClassDefiner() {
        LookupClassDefiner lcd = LookupClassDefiner.tryGet();
        if (lcd != null) return lcd;
        return new StoreClassLoader();
    }

    private interface ClassDefiner {
        Class<?> defineClass(String name, byte[] bytes);
    }

    private static class StoreClassLoader extends ClassLoader implements ClassDefiner {
        @Override
        public Class<?> defineClass(String name, byte[] bytes) {
            return defineClass(name, bytes, 0, bytes.length);
        }
    }

    private static class LookupClassDefiner implements ClassDefiner {
        private final MethodHandle defineHiddenClass;

        private LookupClassDefiner(MethodHandle defineHiddenClass) {
            this.defineHiddenClass = defineHiddenClass;
        }

        public static LookupClassDefiner INSTANCE;
        public static boolean CACHED_FAILURE = false;

        public static @Nullable LookupClassDefiner tryGet() {
            if (CACHED_FAILURE) return null;
            if (INSTANCE != null) return INSTANCE;
            try {
                MethodHandles.Lookup lookup = MethodHandles.lookup();
                Class<?> classOptionClass = Class.forName("java.lang.invoke.MethodHandles$Lookup$ClassOption");
                Object emptyClassOptions = Array.newInstance(classOptionClass, 0);
                MethodHandle defineHiddenClass = lookup.findVirtual(MethodHandles.Lookup.class, "defineHiddenClass",
                        MethodType.methodType(
                                MethodHandles.Lookup.class,
                                byte[].class,
                                boolean.class,
                                emptyClassOptions.getClass()
                        ));
                defineHiddenClass = defineHiddenClass.bindTo(lookup);
                defineHiddenClass = MethodHandles.insertArguments(defineHiddenClass, 1,
                        false, emptyClassOptions);
                return INSTANCE = new LookupClassDefiner(defineHiddenClass);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
                CACHED_FAILURE = true;
                return null;
            }
        }

        @Override
        public Class<?> defineClass(String name, byte[] bytes) {
            try {
                MethodHandles.Lookup definedLookup = (MethodHandles.Lookup) defineHiddenClass.invokeExact(bytes);
                return definedLookup.lookupClass();
            } catch (Throwable t) {
                throw Utils.rethrow(t);
            }
        }
    }
}

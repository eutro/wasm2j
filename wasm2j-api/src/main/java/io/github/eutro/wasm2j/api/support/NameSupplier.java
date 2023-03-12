package io.github.eutro.wasm2j.api.support;

import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * An interface for supplying translations of WebAssembly names into appropriate Java names.
 */
public interface NameSupplier {
    /**
     * Get the
     * <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.2.1">internal name</a>
     * for a WebAssembly module name.
     *
     * @param name The module name.
     * @return The class name.
     */
    String className(String name);

    /**
     * Get the
     * <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.2.2">JVM unqualified name</a>
     * for an imported or exported field of a WebAssembly module.
     *
     * @param name The WebAssembly field name.
     * @return The Java method name.
     */
    String fieldName(String name);

    /**
     * Get the
     * <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.2.2">JVM unqualified name</a>
     * for an imported or exported field of a WebAssembly module,
     * prefixed with a word.
     *
     * @param prefix A word to add before the field name.
     * @param name   The WebAssembly field name.
     * @return The Java method name.
     */
    String fieldName(String prefix, String name);

    /**
     * Create a simple name supplier, which will return class names in the given package,
     * with names mangled according to the name mangler, and cases converted according
     * to the styles provided.
     *
     * @param pkgWithSlashes The package name of classes, with forward slashes, not dots, separating parts.
     * @param mangler        The name mangler.
     * @param modSrc         The source case style for modules, the one used by WebAssembly imports.
     * @param modDst         The target case style for modules, which should be used for the output classes.
     * @param fieldSrc       The source case style for Wasm fields, the one used for import and export names.
     * @param fieldDst       The target case style for Wasm fields, which should be used for Java methods.
     * @return The name supplier.
     */
    static NameSupplier createSimple(
            String pkgWithSlashes,
            NameMangler mangler,
            CaseStyle modSrc, CaseStyle modDst,
            CaseStyle fieldSrc, CaseStyle fieldDst
    ) {
        return createSimple(
                pkgWithSlashes,
                mangler,
                s -> modSrc.convertTo(modDst, s),
                (p, s) -> fieldSrc.convertTo(fieldDst, p, s)
        );
    }

    /**
     * Create a simple name supplier, which will return class names in the given package,
     * with names mangled according to the name mangler, and module/field names converted
     * with the given functions.
     *
     * @param pkgWithSlashes The package name of classes, with forward slashes, not dots, separating parts.
     * @param mangler        The name mangler.
     * @param modConv        The function for converting module names to Java class names, excluding
     *                       part of the package name.
     * @param fieldConv      The function for converting Wasm field names to Java method names.
     * @return The name supplier.
     */
    static NameSupplier createSimple(
            String pkgWithSlashes,
            NameMangler mangler,
            Function<String, String> modConv,
            BiFunction<@Nullable String, String, String> fieldConv
    ) {
        return new SimpleNameSupplier(pkgWithSlashes, mangler, modConv, fieldConv);
    }

    /**
     * A simple name supplier.
     *
     * @see #createSimple(String, NameMangler, CaseStyle, CaseStyle, CaseStyle, CaseStyle)
     * @see #createSimple(String, NameMangler, Function, BiFunction)
     */
    class SimpleNameSupplier implements NameSupplier {
        private final String pkgWithSlashes;
        private final NameMangler mangler;
        private final Function<String, String> classConv;
        private final BiFunction<@Nullable String, String, String> fieldConv;

        SimpleNameSupplier(String pkgWithSlashes,
                           NameMangler mangler,
                           Function<String, String> classConv,
                           BiFunction<@Nullable String, String, String> fieldConv) {
            this.pkgWithSlashes = pkgWithSlashes;
            this.mangler = mangler;
            this.classConv = classConv;
            this.fieldConv = fieldConv;
        }

        @Override
        public String className(String name) {
            return pkgWithSlashes + mangler.mangle(classConv.apply(name));
        }

        @Override
        public String fieldName(String name) {
            return mangler.mangle(fieldConv.apply(null, name));
        }

        @Override
        public String fieldName(String prefix, String name) {
            return mangler.mangle(fieldConv.apply(prefix, name));
        }
    }
}

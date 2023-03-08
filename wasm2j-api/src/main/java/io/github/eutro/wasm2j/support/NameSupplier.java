package io.github.eutro.wasm2j.support;

import org.jetbrains.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Function;

public interface NameSupplier {
    String className(String name);

    String fieldName(String name);

    String fieldName(String prefix, String name);

    static NameSupplier createSimple(
            String pkgWithSlashes,
            NameMangler mangler,
            CaseStyle classSrc, CaseStyle classDst,
            CaseStyle fieldSrc, CaseStyle fieldDst
    ) {
        return createSimple(
                pkgWithSlashes,
                mangler,
                s -> classSrc.convertTo(classDst, s),
                (p, s) -> fieldSrc.convertTo(fieldDst, p, s)
        );
    }

    static NameSupplier createSimple(
            String pkgWithSlashes,
            NameMangler mangler,
            Function<String, String> classConv,
            BiFunction<@Nullable String, String, String> fieldConv
    ) {
        return new SimpleNameSupplier(pkgWithSlashes, mangler, classConv, fieldConv);
    }

    class SimpleNameSupplier implements NameSupplier {
        private final String pkgWithSlashes;
        private final NameMangler mangler;
        private final Function<String, String> classConv;
        private final BiFunction<@Nullable String, String, String> fieldConv;

        public SimpleNameSupplier(String pkgWithSlashes,
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

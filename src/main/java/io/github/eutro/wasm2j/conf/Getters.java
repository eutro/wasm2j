package io.github.eutro.wasm2j.conf;

import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.util.IRUtils;
import io.github.eutro.wasm2j.util.ValueGetter;

public class Getters {
    public static final ValueGetter GET_THIS = IRUtils::getThis;

    public static ValueGetter fieldGetter(ValueGetter getter, JavaExts.JavaField field) {
        return ib -> ib.insert(JavaOps.GET_FIELD
                .create(field)
                .insn(getter.get(ib)),
                "field");
    }

    public static ValueGetter staticGetter(JavaExts.JavaField field) {
        if (!field.isStatic) {
            throw new IllegalArgumentException("field must be static");
        }
        return ib -> ib.insert(JavaOps.GET_FIELD.create(field).insn(), "field");
    }
}

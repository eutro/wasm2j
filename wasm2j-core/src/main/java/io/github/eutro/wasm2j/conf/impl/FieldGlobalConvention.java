package io.github.eutro.wasm2j.conf.impl;

import io.github.eutro.wasm2j.conf.api.ExportableConvention;
import io.github.eutro.wasm2j.conf.api.GlobalConvention;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.util.ValueGetter;

public class FieldGlobalConvention extends DelegatingExporter implements GlobalConvention {
    private final ValueGetter target;
    private final JavaExts.JavaField global;

    public FieldGlobalConvention(
            ExportableConvention exporter,
            ValueGetter target,
            JavaExts.JavaField global
    ) {
        super(exporter);
        this.target = target;
        this.global = global;
    }

    @Override
    public void emitGlobalRef(IRBuilder ib, Effect effect) {
        ib.insert(JavaOps.GET_FIELD.create(global)
                .insn(target.get(ib))
                .copyFrom(effect));
    }

    @Override
    public void emitGlobalStore(IRBuilder ib, Effect effect) {
        ib.insert(JavaOps.PUT_FIELD.create(global)
                .insn(target.get(ib), effect.insn().args().get(0))
                .copyFrom(effect));
    }
}

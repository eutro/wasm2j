package io.github.eutro.wasm2j.conf.impl;

import io.github.eutro.wasm2j.conf.api.ExportableConvention;
import io.github.eutro.wasm2j.conf.api.TableConvention;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.util.ValueGetter;

public class ArrayTableConvention extends DelegatingExporter implements TableConvention {
    private final ValueGetter table;

    public ArrayTableConvention(ExportableConvention exporter, ValueGetter table) {
        super(exporter);
        this.table = table;
    }

    @Override
    public void emitTableRef(IRBuilder ib, Effect effect) {
        ib.insert(JavaOps.ARRAY_GET.create()
                .insn(table.get(ib), effect.insn().args.get(0))
                .copyFrom(effect));

    }

    @Override
    public void emitTableStore(IRBuilder ib, Effect effect) {
        ib.insert(JavaOps.ARRAY_SET.create()
                .insn(table.get(ib),
                        effect.insn().args.get(1),
                        effect.insn().args.get(0))
                .assignTo());

    }
}

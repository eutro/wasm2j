package io.github.eutro.wasm2j.conf.impl;

import io.github.eutro.wasm2j.conf.api.ExportableConvention;
import io.github.eutro.wasm2j.conf.api.GlobalConvention;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.util.ValueGetter;
import io.github.eutro.wasm2j.util.ValueGetterSetter;
import io.github.eutro.wasm2j.util.ValueSetter;

/**
 * An {@link GlobalConvention} which implements sets and gets by delegating to
 * {@link ValueGetter} and {@link ValueSetter}s.
 */
public class GetterSetterGlobalConvention extends DelegatingExporter implements GlobalConvention {
    private final ValueGetter getter;
    private final ValueSetter setter;

    /**
     * Create an {@link GetterSetterGlobalConvention} with the given getter and setter.
     *
     * @param exporter The exporter.
     * @param getter The getter.
     * @param setter The setter. May be null if this global is constant.
     */
    public GetterSetterGlobalConvention(ExportableConvention exporter,
                                        ValueGetter getter,
                                        ValueSetter setter) {
        super(exporter);
        this.getter = getter;
        this.setter = setter;
    }

    /**
     * Create an {@link GetterSetterGlobalConvention} with the given getter and setter.
     *
     * @param exporter The exporter.
     * @param getterSetter The getter and the setter.
     */
    public GetterSetterGlobalConvention(ExportableConvention exporter, ValueGetterSetter getterSetter) {
        this(exporter, getterSetter, getterSetter);
    }

    @Override
    public void emitGlobalRef(IRBuilder ib, Effect effect) {
        ib.insert(CommonOps.IDENTITY.insn(getter.get(ib)).copyFrom(effect));
    }

    @Override
    public void emitGlobalStore(IRBuilder ib, Effect effect) {
        setter.set(ib, effect.insn().args().get(0));
    }
}

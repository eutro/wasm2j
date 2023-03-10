package io.github.eutro.wasm2j.conf.impl;

import io.github.eutro.jwasm.tree.ExportNode;
import io.github.eutro.wasm2j.conf.api.ExportableConvention;
import io.github.eutro.wasm2j.ext.DelegatingExtHolder;
import io.github.eutro.wasm2j.ext.ExtContainer;
import io.github.eutro.wasm2j.ssa.JClass;
import io.github.eutro.wasm2j.ssa.Module;

public class DelegatingExporter extends DelegatingExtHolder implements ExportableConvention {
    private final ExportableConvention exporter;

    public DelegatingExporter(ExportableConvention exporter) {
        this.exporter = exporter;
    }

    @Override
    public void export(ExportNode node, Module module, JClass jClass) {
        if (exporter == null) {
            throw new UnsupportedOperationException();
        }
        exporter.export(node, module, jClass);
    }

    @Override
    protected ExtContainer getDelegate() {
        return exporter instanceof ExtContainer ? (ExtContainer) exporter : null;
    }
}

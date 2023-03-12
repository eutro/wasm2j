package io.github.eutro.wasm2j.core.conf.impl;

import io.github.eutro.jwasm.tree.ExportNode;
import io.github.eutro.wasm2j.core.conf.api.ExportableConvention;
import io.github.eutro.wasm2j.core.ssa.Module;
import io.github.eutro.wasm2j.core.ext.DelegatingExtHolder;
import io.github.eutro.wasm2j.core.ext.ExtContainer;
import io.github.eutro.wasm2j.core.ssa.JClass;

/**
 * An {@link ExportableConvention} which just delegates to another.
 */
public class DelegatingExporter extends DelegatingExtHolder implements ExportableConvention {
    private final ExportableConvention exporter;

    /**
     * Construct a {@link DelegatingExporter} that delegates to the given exporter.
     *
     * @param exporter The exporter.
     */
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

package io.github.eutro.wasm2j.conf.impl;

import io.github.eutro.jwasm.tree.ExportNode;
import io.github.eutro.wasm2j.conf.api.ExportableConvention;

public class DelegatingExporter implements ExportableConvention {
    private final ExportableConvention exporter;

    public DelegatingExporter(ExportableConvention exporter) {
        this.exporter = exporter;
    }

    @Override
    public void export(ExportNode node) {
        if (exporter == null) {
            throw new UnsupportedOperationException();
        }
        exporter.export(node);
    }
}

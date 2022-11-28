package io.github.eutro.wasm2j.conf.impl;

import io.github.eutro.jwasm.tree.ExportNode;
import io.github.eutro.wasm2j.conf.api.ExportableConvention;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ssa.Module;

public class DelegatingExporter implements ExportableConvention {
    private final ExportableConvention exporter;

    public DelegatingExporter(ExportableConvention exporter) {
        this.exporter = exporter;
    }

    @Override
    public void export(ExportNode node, Module module, JavaExts.JavaClass jClass) {
        if (exporter == null) {
            throw new UnsupportedOperationException();
        }
        exporter.export(node, module, jClass);
    }
}
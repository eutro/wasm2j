package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.jwasm.tree.ExportNode;
import io.github.eutro.wasm2j.ext.JavaExts;
import org.objectweb.asm.Opcodes;

public interface ExportableConvention {
    void export(ExportNode node);

    static ExportableConvention fieldExporter(JavaExts.JavaField field) {
        return node -> {
            field.otherAccess = (field.otherAccess & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
            field.name = node.name;
        };
    }

    static ExportableConvention methodExporter(JavaExts.JavaMethod method) {
        return node -> {
            method.name = node.name;
            method.type = JavaExts.JavaMethod.Type.VIRTUAL;
        };
    }
}

package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.jwasm.tree.ExportNode;
import io.github.eutro.wasm2j.ext.JavaExts;
import org.objectweb.asm.Opcodes;

public interface ExportableConvention {
    void export(ExportNode node);

    static String mangle(String name) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i = name.offsetByCodePoints(i, 1)) {
            int ch = name.codePointAt(i);
            if (".;[/<>".indexOf(ch) != -1) {
                sb.append('_');
            } else {
                sb.append(Character.toChars(ch));
            }
        }
        return sb.toString();
    }

    static ExportableConvention fieldExporter(JavaExts.JavaField field) {
        return node -> {
            field.otherAccess = (field.otherAccess & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
            field.name = mangle(node.name);
        };
    }

    static ExportableConvention methodExporter(JavaExts.JavaMethod method) {
        return node -> {
            method.name = mangle(node.name);
            method.type = JavaExts.JavaMethod.Type.VIRTUAL;
        };
    }
}

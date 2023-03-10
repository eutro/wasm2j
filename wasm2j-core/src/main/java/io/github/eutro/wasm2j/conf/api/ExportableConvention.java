package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.jwasm.tree.ExportNode;
import io.github.eutro.wasm2j.ssa.JClass;
import io.github.eutro.wasm2j.ssa.Module;
import org.objectweb.asm.Opcodes;

public interface ExportableConvention {
    void export(ExportNode node, Module module, JClass jClass);

    static String mangle(String name) {
        if (name.isEmpty()) {
            return "epsilon";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i = name.offsetByCodePoints(i, 1)) {
            int ch = name.codePointAt(i);
            if (".;[/<>".indexOf(ch) != -1
                    // space is technically allowed, but it's illegal in most JVM languages
                    || Character.isWhitespace(ch)
                    // same here
                    || ch == '-'
            ) {
                sb.append('_');
            } else {
                // again, most languages aren't a huge fan of digits at the start
                if (i == 0 && Character.isDigit(ch)) {
                    sb.append("_");
                }
                sb.append(Character.toChars(ch));
            }
        }
        return sb.toString();
    }

    static ExportableConvention fieldExporter(JClass.JavaField field) {
        return (node, module, jClass) -> {
            field.otherAccess = (field.otherAccess & ~Opcodes.ACC_PRIVATE) | Opcodes.ACC_PUBLIC;
            field.name = mangle(node.name);
        };
    }

    static ExportableConvention methodExporter(JClass.JavaMethod method) {
        return (node, module, jClass) -> {
            method.name = mangle(node.name);
            method.kind = JClass.JavaMethod.Kind.VIRTUAL;
        };
    }
}

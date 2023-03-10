package io.github.eutro.wasm2j.conf;

import io.github.eutro.jwasm.tree.FuncImportNode;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.jwasm.tree.TypeNode;
import io.github.eutro.wasm2j.conf.api.CallingConvention;
import io.github.eutro.wasm2j.conf.api.FunctionConvention;
import io.github.eutro.wasm2j.conf.api.ImportFactory;
import io.github.eutro.wasm2j.conf.impl.InstanceFunctionConvention;
import io.github.eutro.wasm2j.ext.WasmExts;
import io.github.eutro.wasm2j.ssa.JClass;
import io.github.eutro.wasm2j.util.ValueGetter;
import org.objectweb.asm.Type;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.github.eutro.wasm2j.conf.Getters.GET_THIS;

public class Imports {
    public static ImportFactory<FuncImportNode, FunctionConvention>
    abstractMethodFuncImports(CallingConvention cc) {
        return (module, funcImport, jClass, idx) -> {
            ModuleNode node = module.getExtOrThrow(WasmExts.MODULE);

            assert node.types != null;
            JClass.JavaMethod method = new JClass.JavaMethod(
                    jClass,
                    funcImport.name,
                    cc.getDescriptor(node.types.types.get(funcImport.type))
                            .getDescriptor(),
                    JClass.JavaMethod.Kind.ABSTRACT
            );
            return new InstanceFunctionConvention(
                    null,
                    GET_THIS,
                    method,
                    cc
            );
        };
    }

    public static ImportFactory<FuncImportNode, FunctionConvention>
    interfaceFuncImports(
            ValueGetter getter,
            JClass iFace,
            CallingConvention cc
    ) {
        Map<String, Map<String, JClass.JavaMethod>> index = new HashMap<>();
        AtomicBoolean indexed = new AtomicBoolean();
        return (module, funcImport, jClass, idx) -> {
            if (!indexed.get()) {
                synchronized (indexed) {
                    doIndex:
                    {
                        if (indexed.get()) break doIndex;
                        for (JClass.JavaMethod method : iFace.methods) {
                            index.computeIfAbsent(method.name, $ -> new HashMap<>())
                                    .put(method.getDescriptor(), method);
                        }
                        indexed.set(true);
                    }
                }
            }

            ModuleNode node = module.getExtOrThrow(WasmExts.MODULE);

            Map<String, JClass.JavaMethod> foundByName = index.get(funcImport.name);
            if (foundByName == null) {
                throw new RuntimeException("import " + funcImport.name + " not resolved");
            }

            assert node.types != null;
            TypeNode funcType = node.types.types.get(funcImport.type);
            Type targetDescriptor = cc.getDescriptor(funcType);
            JClass.JavaMethod foundMethod = foundByName.get(targetDescriptor.getDescriptor());
            if (foundMethod == null) {
                throw new RuntimeException(
                        "import " + funcImport.name + " exists in interface, "
                                + "but none of the methods matched the expected descriptor: "
                                + targetDescriptor
                );
            }

            return new InstanceFunctionConvention(
                    null,
                    getter,
                    foundMethod,
                    cc
            );
        };
    }
}

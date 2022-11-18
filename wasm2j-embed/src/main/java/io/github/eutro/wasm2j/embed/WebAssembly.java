package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.sexp.Parser;
import io.github.eutro.jwasm.sexp.Reader;
import io.github.eutro.jwasm.tree.AbstractImportNode;
import io.github.eutro.jwasm.tree.ExportNode;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.embed.internal.WasmConvertPass;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.passes.IRPass;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

public class WebAssembly {

    public static final JavaExts.JavaClass EV_CLASS = new JavaExts.JavaClass(Type.getInternalName(ExternVal.class));

    private File debugOutput;

    public void setDebugOutputDirectory(File file) {
        this.debugOutput = file;
    }

    public Store storeInit() {
        return new Store();
    }

    public Module moduleDecode(byte[] bytes) {
        ModuleNode node = new ModuleNode();
        ModuleReader.fromBytes(bytes).accept(node);
        return new Module(node);
    }

    public Module moduleParse(String source) {
        List<Object> objs = Reader.readAll(source);
        if (objs.size() != 1) throw new IllegalArgumentException("Too many modules in string");
        ModuleNode node = Parser.parseModule(objs.get(0));
        return new Module(node);
    }

    public Module moduleFromNode(ModuleNode node) {
        // protect from mutation, since that could break validation
        ModuleNode copiedNode = new ModuleNode();
        node.accept(copiedNode);
        return new Module(copiedNode);
    }

    public void moduleValidate(Module module) {
        module.validate();
    }

    private final IRPass<ModuleNode, ClassNode> PASS = WasmConvertPass.getPass();

    public ModuleInst moduleInstantiate(Store store, Module module, ExternVal[] imports) {
        module.validate();

        {
            List<ModuleImport> importVals = moduleImports(module);
            if (importVals.size() != imports.length) {
                throw new IllegalArgumentException(String.format("Import lengths mismatch, got: %d, expected: %s",
                        imports.length,
                        importVals));
            }
            for (int i = 0; i < imports.length; i++) {
                if (importVals.get(i).type != imports[i].getType()) {
                    throw new IllegalArgumentException(String.format("Import types mismatch, got: %s, expected: %s",
                            Arrays.stream(imports)
                                    .map(ExternVal::getType)
                                    .map(Objects::toString)
                                    .collect(Collectors.joining(", ", "[", "]")),
                            importVals
                    ));
                }
            }
        }

        Class<?> moduleClass;
        try {
            ClassNode classNode = PASS.run(module.getNode());
            classNode.interfaces.add(Type.getInternalName(ModuleInst.class));
            moduleClass = store.defineClass(classNode, debugOutput);
        } catch (OutOfMemoryError e) {
            // chances are it was us here doing horrible things, so yield an exception instead of the error
            throw new ModuleRefusedException(e);
        }
        Constructor<?> ctor = moduleClass.getConstructors()[0];

        Object inst;
        try {
            inst = ctor.newInstance((Object[]) imports);
        } catch (InvocationTargetException ite) {
            throw new RuntimeException("Error instantiating module", ite.getCause());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalStateException("Internal error instantiating module. This is a bug.", e);
        }
        return (ModuleInst) inst;
    }

    public List<ModuleImport> moduleImports(Module module) {
        module.validate();
        if (module.getNode().imports == null) return Collections.emptyList();
        List<ModuleImport> ims = new ArrayList<>();
        for (AbstractImportNode iNode : module.getNode().imports) {
            ims.add(new ModuleImport(
                    iNode.module,
                    iNode.name,
                    ExternType.fromByte(iNode.importType())
            ));
        }
        return ims;
    }

    public List<ModuleExport> moduleExports(Module module) {
        module.validate();
        if (module.getNode().exports == null) return Collections.emptyList();
        List<ModuleExport> exs = new ArrayList<>();
        for (ExportNode export : module.getNode().exports) {
            exs.add(new ModuleExport(
                    export.name,
                    ExternType.fromByte(export.type)
            ));
        }
        return exs;
    }

    public ExternVal instanceExport(ModuleInst inst, String name) {
        return inst.getExport(name);
    }
}

package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.sexp.Parser;
import io.github.eutro.jwasm.sexp.Reader;
import io.github.eutro.jwasm.tree.AbstractImportNode;
import io.github.eutro.jwasm.tree.ExportNode;
import io.github.eutro.jwasm.tree.ExportsNode;
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

    public Instance moduleInstantiate(Store store, Module module, ExternVal[] imports) {
        module.validate();

        {
            List<Import> declaredImports = moduleImports(module);
            if (declaredImports.size() != imports.length) {
                throw new IllegalArgumentException(String.format("Import lengths mismatch, got: %d, expected: %s",
                        imports.length,
                        declaredImports));
            }
            for (int i = 0; i < imports.length; i++) {
                if (!declaredImports.get(i).type.assignableFrom(imports[i].getType())) {
                    throw new IllegalArgumentException(String.format("Import types mismatch, got: %s, expected: %s",
                            Arrays.stream(imports)
                                    .map(ExternVal::getType)
                                    .map(Objects::toString)
                                    .collect(Collectors.joining(", ", "[", "]")),
                            declaredImports
                    ));
                }
            }
        }

        Class<?> moduleClass;
        try {
            ClassNode classNode = PASS.run(module.getNode());
            classNode.interfaces.add(Type.getInternalName(Instance.class));
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
        return (Instance) inst;
    }

    public List<Import> moduleImports(Module module) {
        module.validate();
        if (module.getNode().imports == null) return Collections.emptyList();
        List<Import> ims = new ArrayList<>();
        for (AbstractImportNode iNode : module.getNode().imports) {
            ims.add(new Import(
                    iNode.module,
                    iNode.name,
                    ExternType.fromImport(iNode, module.getNode())
            ));
        }
        return ims;
    }

    public List<Export> moduleExports(Module module) {
        module.validate();
        ExportsNode exports = module.getNode().exports;
        if (exports == null || exports.exports.isEmpty()) return Collections.emptyList();
        EnumMap<ExternType.Kind, List<ExternType>> imported = new EnumMap<>(ExternType.Kind.class);
        for (Import moduleImport : moduleImports(module)) {
            imported.computeIfAbsent(moduleImport.type.getKind(), $ -> new ArrayList<>()).add(moduleImport.type);
        }
        List<Export> exs = new ArrayList<>();
        for (ExportNode export : exports) {
            ExternType.Kind kind = ExternType.Kind.fromByte(export.type);
            List<ExternType> importedOfKind = imported.getOrDefault(kind, Collections.emptyList());
            exs.add(new Export(
                    export.name,
                    export.index < importedOfKind.size()
                            ? importedOfKind.get(export.index)
                            : ExternType.getLocal(module.getNode(),
                            kind,
                            export.index - importedOfKind.size())
            ));
        }
        return exs;
    }

    public ExternVal instanceExport(Instance inst, String name) {
        return inst.getExport(name);
    }
}

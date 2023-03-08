package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.sexp.Parser;
import io.github.eutro.jwasm.sexp.Reader;
import io.github.eutro.jwasm.tree.AbstractImportNode;
import io.github.eutro.jwasm.tree.ExportNode;
import io.github.eutro.jwasm.tree.ExportsNode;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.jwasm.tree.analysis.ModuleValidator;
import io.github.eutro.wasm2j.support.ExternType;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Module {
    private final ModuleNode node;
    private boolean validated = false;

    Module(ModuleNode node) {
        this.node = node;
    }

    @Embedding("module_decode")
    public static Module decode(byte[] bytes) {
        ModuleNode node = new ModuleNode();
        ModuleReader.fromBytes(bytes).accept(node);
        return new Module(node);
    }

    @Embedding("module_parse")
    public static Module parse(String source) {
        List<Object> objs = Reader.readAll(source);
        if (objs.size() != 1) throw new IllegalArgumentException("Too many modules in string");
        ModuleNode node = Parser.parseModule(objs.get(0));
        return new Module(node);
    }

    public static Module fromNode(ModuleNode node) {
        // protect from mutation, since that could break validation
        ModuleNode copiedNode = new ModuleNode();
        node.accept(copiedNode);
        return new Module(copiedNode);
    }

    ModuleNode getNode() {
        return node;
    }

    @Embedding("module_validate")
    public void validate() {
        if (!validated) {
            node.accept(new ModuleValidator());
            validated = true;
        }
    }

    @Embedding("module_instantiate")
    public Instance instantiate(Store store, ExternVal[] imports) {
        validate();

        {
            List<Import> declaredImports = imports();
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
            ClassNode classNode = store.PASS.run(getNode());
            classNode.interfaces.add(Type.getInternalName(Instance.class));
            moduleClass = store.defineClass(classNode);
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

    public Instance instantiate(Store store, BiFunction<String, String, ExternVal> imports) {
        List<Import> myImports = imports();
        ExternVal[] importList = new ExternVal[myImports.size()];
        int i = 0;
        for (Import theImport : myImports) {
            importList[i++] = imports.apply(theImport.module, theImport.name);
        }
        return instantiate(store, importList);
    }

    public Instance instantiate(Store store, Function<String, Instance> modules) {
        return instantiate(store, (module, name) -> {
            Instance instance = modules.apply(module);
            if (instance == null) throw new RuntimeException(String.format("Module '%s' not provided", module));
            ExternVal value = instance.getExport(name);
            if (value == null) throw new RuntimeException(String.format("Module '%s' does not provide anything named '%s'", module, name));
            return value;
        });
    }

    @Embedding("module_imports")
    public List<Import> imports() {
        validate();
        if (getNode().imports == null) return Collections.emptyList();
        List<Import> ims = new ArrayList<>();
        for (AbstractImportNode iNode : getNode().imports) {
            ims.add(new Import(
                    iNode.module,
                    iNode.name,
                    ExternType.fromImport(iNode, getNode())
            ));
        }
        return ims;
    }

    @Embedding("module_exports")
    public List<Export> exports() {
        validate();
        ExportsNode exports = getNode().exports;
        if (exports == null || exports.exports.isEmpty()) return Collections.emptyList();
        EnumMap<ExternType.Kind, List<ExternType>> imported = new EnumMap<>(ExternType.Kind.class);
        for (Import moduleImport : imports()) {
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
                            : ExternType.getLocal(getNode(), kind, export.index - importedOfKind.size())
            ));
        }
        return exs;
    }
}

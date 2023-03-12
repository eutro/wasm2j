package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.sexp.WatParser;
import io.github.eutro.jwasm.sexp.WatReader;
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

/**
 * A parsed WebAssembly module.
 */
public class Module {
    private final ModuleNode node;
    private boolean validated = false;

    Module(ModuleNode node) {
        this.node = node;
    }

    /**
     * Decode a binary module from a byte array.
     *
     * @param bytes The byte array.
     * @return The decoded module
     */
    @Embedding("module_decode")
    public static Module decode(byte[] bytes) {
        ModuleNode node = new ModuleNode();
        ModuleReader.fromBytes(bytes).accept(node);
        return new Module(node);
    }

    /**
     * Parse a text module from a string.
     *
     * @param source The source string.
     * @return The parsed module.
     */
    @Embedding("module_parse")
    public static Module parse(String source) {
        List<Object> objs = WatReader.readAll(source);
        if (objs.size() != 1) throw new IllegalArgumentException("Too many modules in string");
        ModuleNode node = WatParser.DEFAULT.parseModule(objs.get(0));
        return new Module(node);
    }

    /**
     * Create a module from a JWasm module node.
     * <p>
     * The module node will be copied, to prevent exterior mutation.
     *
     * @param node The module node.
     * @return The new module.
     */
    public static Module fromNode(ModuleNode node) {
        // protect from mutation, since that could break validation
        ModuleNode copiedNode = new ModuleNode();
        node.accept(copiedNode);
        return new Module(copiedNode);
    }

    ModuleNode getNode() {
        return node;
    }

    /**
     * Validate the module, throwing an exception if the module is invalid.
     * <p>
     * This is idempotent, and will not run validation again if the module has already been validated.
     */
    @Embedding("module_validate")
    public void validate() {
        if (!validated) {
            node.accept(new ModuleValidator());
            validated = true;
        }
    }

    /**
     * Instantiate the module in the given store with the provided values for imports.
     * <p>
     * The imports must be in the same order as those returned for {@link #imports()}.
     *
     * @param store   The store.
     * @param imports The supplied imports.
     * @return The instantiated module.
     */
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

    /**
     * Instantiate the module in the given store with the provided function for looking up imports.
     *
     * @param store   The store.
     * @param imports The import resolving function.
     * @return The instantiated module.
     */
    public Instance instantiate(Store store, BiFunction<String, String, ExternVal> imports) {
        List<Import> myImports = imports();
        ExternVal[] importList = new ExternVal[myImports.size()];
        int i = 0;
        for (Import theImport : myImports) {
            importList[i++] = imports.apply(theImport.module, theImport.name);
        }
        return instantiate(store, importList);
    }

    /**
     * Instantiate the module in the given store with the provided function for looking up modules to import from.
     *
     * @param store   The store.
     * @param modules The function for looking up module instances.
     * @return The instantiated module.
     */
    public Instance instantiate(Store store, Function<String, Instance> modules) {
        return instantiate(store, (module, name) -> {
            Instance instance = modules.apply(module);
            if (instance == null) throw new RuntimeException(String.format("Module '%s' not provided", module));
            ExternVal value = instance.getExport(name);
            if (value == null)
                throw new RuntimeException(String.format("Module '%s' does not provide anything named '%s'", module, name));
            return value;
        });
    }

    /**
     * Get the imports that this module requires.
     *
     * @return The list of imports.
     */
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

    /**
     * Get the exports that this module supplies.
     *
     * @return The list of exports.
     */
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

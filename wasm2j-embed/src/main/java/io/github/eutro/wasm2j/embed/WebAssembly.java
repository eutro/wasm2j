package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.Opcodes;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.api.types.ExternType;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.util.List;

/**
 * A direct implementation of the WebAssembly embedding entry-points as defined in the
 * <a href="https://webassembly.github.io/spec/core/appendix/embedding.html">embedding section</a>
 * of the specification.
 * <p>
 * Clients do <em>not</em> need to use this class, and are free to directly invoke
 * the more convenient and granular methods and constructors of the relevant classes themselves.
 */
public class WebAssembly {
    private File debugOutput;

    /**
     * Set the debug output of stores created by this embedding instance.
     *
     * @param file The debug directory.
     * @see Store#setDebugOutput(File)
     */
    public void setDebugOutputDirectory(File file) {
        this.debugOutput = file;
    }

    /**
     * Returns a new empty store.
     *
     * @return The new store.
     * @see Store#init()
     */
    @Embedding("store_init")
    public Store storeInit() {
        Store store = Store.init();
        store.setDebugOutput(debugOutput);
        return store;
    }

    /**
     * Decode a binary module from a byte array.
     *
     * @param bytes The byte array.
     * @return The decoded module
     * @see Module#decode(byte[])
     */
    @Embedding("module_decode")
    public Module moduleDecode(byte[] bytes) {
        return Module.decode(bytes);
    }

    /**
     * Parse a text module from a string.
     *
     * @param source The source string.
     * @return The parsed module.
     * @see Module#parse(String)
     */
    @Embedding("module_parse")
    public Module moduleParse(String source) {
        return Module.parse(source);
    }

    /**
     * Create a module from a JWasm module node.
     * <p>
     * The module node will be copied, to prevent exterior mutation.
     *
     * @param node The module node.
     * @return The new module.
     * @see Module#fromNode(ModuleNode)
     */
    public Module moduleFromNode(ModuleNode node) {
        return Module.fromNode(node);
    }

    /**
     * Validate the module, throwing an exception if the module is invalid.
     * <p>
     * This is idempotent, and will not run validation again if the module has already been validated.
     *
     * @param module The module to validate.
     * @see Module#validate()
     */
    @Embedding("module_validate")
    public void moduleValidate(Module module) {
        module.validate();
    }

    /**
     * Instantiate the module in the given store with the provided values for imports.
     * <p>
     * The imports must be in the same order as those returned for {@link Module#imports()}.
     *
     * @param store   The store.
     * @param module  The module to instantiate.
     * @param imports The supplied imports.
     * @return The instantiated module.
     * @see Module#instantiate(Store, ExternVal[])
     */
    @Embedding("module_instantiate")
    public Instance moduleInstantiate(Store store, Module module, ExternVal[] imports) {
        return module.instantiate(store, imports);
    }

    /**
     * Get the imports that the module requires.
     *
     * @param module The module.
     * @return The list of imports.
     * @see Module#imports()
     */
    @Embedding("module_imports")
    public List<Import> moduleImports(Module module) {
        return module.imports();
    }

    /**
     * Get the exports that this module supplies.
     *
     * @param module The module.
     * @return The list of exports.
     * @see Module#exports()
     */
    @Embedding("module_exports")
    public List<Export> moduleExports(Module module) {
        return module.exports();
    }

    /**
     * Get a named export of the module instance.
     *
     * @param inst The module instance.
     * @param name The name of the export.
     * @return The export.
     * @see Instance#getExport(String)
     */
    @Embedding("instance_export")
    public ExternVal instanceExport(Instance inst, String name) {
        return inst.getExport(name);
    }

    /**
     * Construct a new function extern.
     *
     * @param store  The store. Ignored.
     * @param type   The function type.
     * @param handle The method handle.
     * @return The allocated function.
     * @see Func.HandleFunc#HandleFunc(ExternType.Func, MethodHandle)
     */
    @Embedding("func_alloc")
    public Func funcAlloc(
            @SuppressWarnings("unused") @Nullable Store store,
            ExternType.Func type,
            MethodHandle handle
    ) {
        return new Func.HandleFunc(type, handle);
    }

    /**
     * Get the type of a function extern.
     *
     * @param store The store. Ignored.
     * @param func  The function.
     * @return The type of the function.
     * @see Func#getType()
     */
    @Embedding("func_type")
    public ExternType.Func funcType(
            @SuppressWarnings("unused") @Nullable Store store,
            Func func
    ) {
        return func.getType();
    }

    /**
     * Invoke a function extern.
     *
     * @param store The store. Ignored.
     * @param func  The function.
     * @param args  The function arguments.
     * @return The type of the function.
     * @see Func#invoke(Object...)
     */
    @Embedding("func_invoke")
    public Object[] funcInvoke(
            @SuppressWarnings("unused") @Nullable Store store,
            Func func,
            Object... args
    ) {
        return func.invoke(args);
    }

    /**
     * Construct a new table with the given type.
     *
     * @param store The store. Ignored.
     * @param type  The table type.
     * @return A new table.
     * @see Table.ArrayTable#ArrayTable(ExternType.Table)
     */
    @Embedding("table_alloc")
    public Table tableAlloc(
            @SuppressWarnings("unused") @Nullable Store store,
            ExternType.Table type
    ) {
        return new Table.ArrayTable(type);
    }

    /**
     * Get the type of a table extern.
     *
     * @param store The store. Ignored.
     * @param table The table.
     * @return The table's type.
     * @see Table#getType()
     */
    @Embedding("table_type")
    public ExternType.Table tableType(
            @SuppressWarnings("unused") @Nullable Store store,
            Table table
    ) {
        return table.getType();
    }

    /**
     * Get an element of the table.
     *
     * @param store The store. Ignored.
     * @param table The table.
     * @param i     The element index.
     * @return The element.
     * @see Table#get(int)
     */
    @Embedding("table_read")
    public Object tableRead(
            @SuppressWarnings("unused") @Nullable Store store,
            Table table,
            int i
    ) {
        return table.get(i);
    }

    /**
     * Set an element of the table.
     *
     * @param store The store. Ignored.
     * @param table The table.
     * @param i     The element index.
     * @param value The element.
     * @see Table#set(int, Object)
     */
    @Embedding("table_write")
    public void tableWrite(
            @SuppressWarnings("unused") @Nullable Store store,
            Table table,
            int i,
            Object value
    ) {
        table.set(i, value);
    }

    /**
     * Get the size of a table.
     *
     * @param store The store. Ignored.
     * @param table The table.
     * @return The size of the table.
     * @see Table#size()
     */
    @Embedding("table_size")
    public int tableSize(
            @SuppressWarnings("unused") @Nullable Store store,
            Table table
    ) {
        return table.size();
    }

    /**
     * Grow the table by a number of elements, filling it with the given value.
     * <p>
     * This operation is allowed to fail (and must fail if the upper limit is exceeded),
     * in which case -1 should be returned.
     *
     * @param store    The store. Ignored.
     * @param table    The table.
     * @param growBy   The number of elements to grow by.
     * @param fillWith The value to fill new elements with.
     * @return The old size of the table.
     * @see Table#grow(int, Object)
     */
    @Embedding("table_grow")
    public int tableGrow(
            @SuppressWarnings("unused") @Nullable Store store,
            Table table,
            int growBy,
            Object fillWith
    ) {
        return table.grow(growBy, fillWith);
    }

    /**
     * Create a new memory with the given type.
     *
     * @param store The store. Ignored.
     * @param type  The type.
     * @return The new memory.
     * @see Memory#alloc(ExternType.Mem)
     */
    @Embedding("mem_alloc")
    public Memory memAlloc(
            @SuppressWarnings("unused") @Nullable Store store,
            ExternType.Mem type
    ) {
        return Memory.alloc(type);
    }

    /**
     * Get the type of a memory.
     *
     * @param store  The store. Ignored.
     * @param memory The memory.
     * @return The memory type.
     * @see Memory#getType()
     */
    @Embedding("mem_type")
    public ExternType.Mem memType(
            @SuppressWarnings("unused") @Nullable Store store,
            Memory memory
    ) {
        return memory.getType();
    }

    /**
     * Read a single byte from a memory.
     * <p>
     * Using this is discouraged, see {@link Memory#read(int)}.
     *
     * @param store  The store. Ignored.
     * @param memory The memory.
     * @param addr   The memory address.
     * @return The byte.
     * @see Memory#read(int)
     */
    @Embedding("mem_read")
    public byte memRead(
            @SuppressWarnings("unused") @Nullable Store store,
            Memory memory,
            int addr
    ) {
        return memory.read(addr);
    }

    /**
     * Write a single byte to a memory.
     * <p>
     * Using this is discouraged, see {@link Memory#write(int, byte)}.
     *
     * @param store  The store. Ignored.
     * @param memory The memory.
     * @param addr   The memory address.
     * @param value  The byte.
     * @see Memory#write(int, byte)
     */
    @Embedding("mem_write")
    public void memWrite(
            @SuppressWarnings("unused") @Nullable Store store,
            Memory memory,
            int addr,
            byte value
    ) {
        memory.write(addr, value);
    }

    /**
     * Get the size of the memory, in pages.
     *
     * @param store  The store. Ignored.
     * @param memory The memory.
     * @return The size of the memory, in pages.
     * @see Memory#size()
     * @see Opcodes#PAGE_SIZE
     */
    @Embedding("mem_size")
    public int memSize(
            @SuppressWarnings("unused") @Nullable Store store,
            Memory memory
    ) {
        return memory.size();
    }

    /**
     * Grow the memory by a number of pages, filling new pages with 0.
     * <p>
     * This operation is allowed to fail (and must fail if the upper limit is exceeded),
     * in which case -1 should be returned.
     *
     * @param store       The store. Ignored.
     * @param memory      The memory.
     * @param growByPages The number of pages to grow by, interpreted as an unsigned integer.
     * @return The old size, or -1 if the memory was not grown.
     * @see Memory#size()
     * @see Opcodes#PAGE_SIZE
     */
    @Embedding("mem_grow")
    public int memGrow(
            @SuppressWarnings("unused") @Nullable Store store,
            Memory memory,
            int growByPages
    ) {
        return memory.grow(growByPages);
    }

    /**
     * Create a new global with the given type.
     *
     * @param store The store. Ignored.
     * @param type  The type.
     * @return The global.
     * @see Global.BoxGlobal#BoxGlobal(ExternType.Global)
     */
    @Embedding("global_alloc")
    public Global globalAlloc(
            @SuppressWarnings("unused") @Nullable Store store,
            ExternType.Global type
    ) {
        return new Global.BoxGlobal(type);
    }

    /**
     * Get the type of a global.
     *
     * @param store  The store. Ignored.
     * @param global The global.
     * @return The global type.
     * @see Global#getType()
     */
    @Embedding("global_type")
    public ExternType.Global globalType(
            @SuppressWarnings("unused") @Nullable Store store,
            Global global
    ) {
        return global.getType();
    }

    /**
     * Read the value of a global.
     *
     * @param store  The store. Ignored.
     * @param global The global.
     * @return The value of the global.
     * @see Global#get()
     */
    @Embedding("global_read")
    public Object globalRead(
            @SuppressWarnings("unused") @Nullable Store store,
            Global global
    ) {
        return global.get();
    }

    /**
     * Write the value of a global.
     * <p>
     * Should fail if the global is immutable.
     *
     * @param store  The store. Ignored.
     * @param global The global.
     * @param value  The new value of the global.
     * @see Global#set(Object)
     */
    @Embedding("global_write")
    public void globalWrite(
            @SuppressWarnings("unused") @Nullable Store store,
            Global global,
            Object value
    ) {
        global.set(value);
    }
}

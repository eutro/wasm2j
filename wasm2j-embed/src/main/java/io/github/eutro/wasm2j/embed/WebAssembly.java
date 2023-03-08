package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.support.ExternType;
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

    public void setDebugOutputDirectory(File file) {
        this.debugOutput = file;
    }

    @Embedding("store_init")
    public Store storeInit() {
        Store store = Store.init();
        store.setDebugOutput(debugOutput);
        return store;
    }

    @Embedding("module_decode")
    public Module moduleDecode(byte[] bytes) {
        return Module.decode(bytes);
    }

    @Embedding("module_parse")
    public Module moduleParse(String source) {
        return Module.parse(source);
    }

    public Module moduleFromNode(ModuleNode node) {
        return Module.fromNode(node);
    }

    @Embedding("module_validate")
    public void moduleValidate(Module module) {
        module.validate();
    }

    @Embedding("module_instantiate")
    public Instance moduleInstantiate(Store store, Module module, ExternVal[] imports) {
        return module.instantiate(store, imports);
    }

    @Embedding("module_imports")
    public List<Import> moduleImports(Module module) {
        return module.imports();
    }

    @Embedding("module_exports")
    public List<Export> moduleExports(Module module) {
        return module.exports();
    }

    @Embedding("instance_export")
    public ExternVal instanceExport(Instance inst, String name) {
        return inst.getExport(name);
    }

    @Embedding("func_alloc")
    public Func funcAlloc(
            @SuppressWarnings("unused") @Nullable Store store,
            ExternType.Func type,
            MethodHandle handle
    ) {
        return new Func.HandleFunc(type, handle);
    }

    @Embedding("func_type")
    public ExternType.Func funcType(
            @SuppressWarnings("unused") @Nullable Store store,
            Func func
    ) {
        return func.getType();
    }

    @Embedding("func_invoke")
    public Object[] funcInvoke(
            @SuppressWarnings("unused") @Nullable Store store,
            Func func,
            Object... args
    ) {
        return func.invoke(args);
    }

    @Embedding("table_alloc")
    public Table tableAlloc(
            @SuppressWarnings("unused") @Nullable Store store,
            ExternType.Table type
    ) {
        return new Table.ArrayTable(type);
    }

    @Embedding("table_type")
    public ExternType.Table tableType(
            @SuppressWarnings("unused") @Nullable Store store,
            Table table
    ) {
        return table.getType();
    }

    @Embedding("table_read")
    public Object tableRead(
            @SuppressWarnings("unused") @Nullable Store store,
            Table table,
            int i
    ) {
        return table.get(i);
    }

    @Embedding("table_write")
    public void tableWrite(
            @SuppressWarnings("unused") @Nullable Store store,
            Table table,
            int i,
            Object value
    ) {
        table.set(i, value);
    }

    @Embedding("table_size")
    public Object tableSize(
            @SuppressWarnings("unused") @Nullable Store store,
            Table table
    ) {
        return table.size();
    }

    @Embedding("table_grow")
    public int tableGrow(
            @SuppressWarnings("unused") @Nullable Store store,
            Table table,
            int growBy,
            Object fillWith
    ) {
        return table.grow(growBy, fillWith);
    }

    @Embedding("mem_alloc")
    public Memory memAlloc(
            @SuppressWarnings("unused") @Nullable Store store,
            ExternType.Mem type
    ) {
        return new Memory.ByteBufferMemory(type);
    }

    @Embedding("mem_type")
    public ExternType.Mem memType(
            @SuppressWarnings("unused") @Nullable Store store,
            Memory memory
    ) {
        return memory.getType();
    }

    @Embedding("mem_read")
    public byte memRead(
            @SuppressWarnings("unused") @Nullable Store store,
            Memory memory,
            int addr
    ) {
        return memory.read(addr);
    }

    @Embedding("mem_write")
    public void memWrite(
            @SuppressWarnings("unused") @Nullable Store store,
            Memory memory,
            int addr,
            byte value
    ) {
        memory.write(addr, value);
    }

    @Embedding("mem_size")
    public int memSize(
            @SuppressWarnings("unused") @Nullable Store store,
            Memory memory
    ) {
        return memory.size();
    }

    @Embedding("mem_grow")
    public int memGrow(
            @SuppressWarnings("unused") @Nullable Store store,
            Memory memory,
            int growByPages
    ) {
        return memory.grow(growByPages);
    }

    @Embedding("global_alloc")
    public Global globalAlloc(
            @SuppressWarnings("unused") @Nullable Store store,
            ExternType.Global type
    ) {
        return new Global.BoxGlobal(type);
    }

    @Embedding("global_type")
    public ExternType.Global globalType(
            @SuppressWarnings("unused") @Nullable Store store,
            Global global
    ) {
        return global.getType();
    }

    @Embedding("global_read")
    public Object globalRead(
            @SuppressWarnings("unused") @Nullable Store store,
            Global global
    ) {
        return global.get();
    }

    @Embedding("global_write")
    public void globalWrite(
            @SuppressWarnings("unused") @Nullable Store store,
            Global global,
            Object value
    ) {
        global.set(value);
    }
}

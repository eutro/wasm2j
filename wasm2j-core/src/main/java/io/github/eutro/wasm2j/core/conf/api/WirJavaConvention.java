package io.github.eutro.wasm2j.core.conf.api;

import io.github.eutro.wasm2j.core.ssa.Function;
import io.github.eutro.wasm2j.core.passes.IRPass;

/**
 * Definitions for how certain WebAssembly concepts should be translated into Java.
 * <p>
 * When compiling a WebAssembly module, different functions, globals, memories, or tables
 * may behave in different ways: some may be local to the compiled module, whereas others
 * might be imported. We say each has its own <i>convention</i>: rules for calling,
 * getting, setting, loading, storing, etc. These can be retrieved with the {@code get*}
 * methods of this interface.
 * <p>
 * * A {@link WirJavaConvention} is typically created by a {@link WirJavaConventionFactory}
 * * for compiling a given WebAssembly module, and encapsulates the conventions
 * * of all the imported and local WebAssembly functions, globals, memories, and tables.
 *
 * @see FunctionConvention
 * @see GlobalConvention
 * @see MemoryConvention
 * @see TableConvention
 * @see DataConvention
 * @see ElemConvention
 * @see CallingConvention
 */
public interface WirJavaConvention {
    /**
     * Get the convention of the function at the given index in the module.
     *
     * @param index The function's index.
     * @return The function's convention.
     */
    FunctionConvention getFunction(int index);

    /**
     * Get the convention of the global at the given index in the module.
     *
     * @param index The global's index.
     * @return The global's convention.
     */
    GlobalConvention getGlobal(int index);

    /**
     * Get the convention of the memory at the given index in the module.
     *
     * @param index The memory's index.
     * @return The memory's convention.
     */
    MemoryConvention getMemory(int index);

    /**
     * Get the convention of the table at the given index in the module.
     *
     * @param index The table's index.
     * @return The table's convention.
     */
    TableConvention getTable(int index);

    /**
     * Get the convention of the data at the given index in the module.
     *
     * @param index The data's index.
     * @return The data's convention.
     */
    DataConvention getData(int index);

    /**
     * Get the convention of the elem at the given index in the module.
     *
     * @param index The elem's index.
     * @return The elem's convention.
     */
    ElemConvention getElem(int index);

    /**
     * Get the calling convention that indirect calls should be made with.
     *
     * @return The calling convention.
     */
    CallingConvention getIndirectCallingConvention();

    /**
     * Run the conversion of the WebAssembly module to a Java class. This should be called exactly once
     * for each {@link WirJavaConvention}.
     *
     * @param convertPass The pass to convert a single WebAssembly-IR function to a Java-IR function.
     */
    void convert(IRPass<Function, Function> convertPass);
}

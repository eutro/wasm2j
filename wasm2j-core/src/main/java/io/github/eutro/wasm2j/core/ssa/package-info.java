/**
 * This package defines the intermediate representation (IR) used by Wasm2j
 * to represent both WebAssembly and Java code.
 * <p>
 * "WebAssembly IR" refers to IR that was recently parsed from WebAssembly code,
 * contains {@link io.github.eutro.wasm2j.core.ops.WasmOps WebAssembly operations},
 * has {@link io.github.eutro.wasm2j.core.ext.WasmExts WebAssembly extensions}, and
 * has its functions aggregated in an {@link io.github.eutro.wasm2j.core.ssa.Module WebAssembly module}.
 * <p>
 * "Java IR", on the other hand, may have been parsed from Java bytecode, or
 * converted from WebAssembly IR. It contains
 * {@link io.github.eutro.wasm2j.core.ops.JavaOps Java operations}, has
 * {@link io.github.eutro.wasm2j.core.ext.JavaExts Java extensions}, and
 * has its functions aggregated in {@link io.github.eutro.wasm2j.core.ssa.JClass Java classes}.
 * <p>
 * In general, "IR" in this code base may refer to any part of the intermediate representation:
 * the functions, the basic blocks, collections of functions (Wasm modules, Java classes),
 * instructions, operations, etc.
 * <p>
 * It is usually in static single assignment form (SSA), hence the name of the package.
 * That is, each {@link io.github.eutro.wasm2j.core.ssa.Var}
 * is assigned to by <i>exactly</i> one {@link io.github.eutro.wasm2j.core.ssa.Effect}
 * instruction, in one of its dominating blocks. This invariant can be assumed
 * to be upheld, unless it is explicitly stated otherwise.
 * <p>
 * The conversion to/from SSA form is handled by passes
 * in {@link io.github.eutro.wasm2j.core.passes.convert}.
 */
package io.github.eutro.wasm2j.core.ssa;
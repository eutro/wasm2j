/**
 * Passes that change entirely the structure of the IR from one form to another,
 * or convert into/out of the IR.
 * <p>
 * Most of these are destructive, in that they render the original IR unusable
 * (in its original form).
 */
package io.github.eutro.wasm2j.core.passes.convert;
/**
 * Events that occur during a compilation.
 * <p>
 * These can be used to configure the compiler,
 * to run extra passes, and the like.
 * <p>
 * The API revolves around {@link io.github.eutro.wasm2j.api.events.EventSupplier}s,
 * which can dispatch (or have dispatched on them) events that subclass a specific type.
 */
package io.github.eutro.wasm2j.api.events;
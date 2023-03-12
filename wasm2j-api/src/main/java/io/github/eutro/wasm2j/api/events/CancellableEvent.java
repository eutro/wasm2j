package io.github.eutro.wasm2j.api.events;

public interface CancellableEvent {
    boolean isCancelled();

    void cancel();
}

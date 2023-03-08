package io.github.eutro.wasm2j.events;

public interface CancellableEvent {
    boolean isCancelled();

    void cancel();
}

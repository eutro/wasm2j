package io.github.eutro.wasm2j.api.events;

/**
 * An event that can be cancelled, preventing other listeners from receiving it.
 */
public interface CancellableEvent {
    /**
     * Get whether the event has been cancelled.
     *
     * @return Whether the event has been cancelled.
     */
    boolean isCancelled();

    /**
     * Cancel the event.
     */
    void cancel();
}

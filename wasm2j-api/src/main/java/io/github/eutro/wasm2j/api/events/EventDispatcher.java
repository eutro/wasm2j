package io.github.eutro.wasm2j.api.events;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * A class on which events can be listened to.
 *
 * @param <S> The type of events that can be listened to.
 */
public interface EventDispatcher<S> {
    /**
     * Listen to the given event type.
     * <p>
     * Only the firing of the <i>exact</i> event type will cause the listener to be run,
     * not subclasses, and not superclasses.
     *
     * @param eventClass The event class.
     * @param listener   The listener.
     * @param <T>        The event type.
     */
    <T extends S> void listen(Class<T> eventClass, @NotNull Consumer<T> listener);
}

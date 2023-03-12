package io.github.eutro.wasm2j.api.events;

import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A class on which events can be listened to and dispatched.
 *
 * @param <S> The type of events that can be listened to or dispatched.
 */
public class EventSupplier<S> implements EventDispatcher<S> {
    private final Map<Class<?>, Set<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    @Override
    public <T extends S> void listen(Class<T> eventClass, @NotNull Consumer<T> listener) {
        listeners.computeIfAbsent(
                eventClass,
                $ -> new LinkedHashSet<>()
        ).add(listener);
    }

    /**
     * Dispatch an event to listeners.
     *
     * @param eventClass The exact type of the event.
     * @param event      The event.
     * @param <T>        The type of the event.
     * @return The event.
     */
    public <T extends S> T dispatch(Class<T> eventClass, T event) {
        @SuppressWarnings("unchecked")
        Set<Consumer<T>> eventListeners = (Set<Consumer<T>>) (Object)
                listeners.getOrDefault(eventClass, Collections.emptySet());
        for (Consumer<T> consumer : eventListeners) {
            if (event instanceof CancellableEvent
                    && ((CancellableEvent) event).isCancelled()) {
                break;
            }
            consumer.accept(event);
        }
        return event;
    }
}

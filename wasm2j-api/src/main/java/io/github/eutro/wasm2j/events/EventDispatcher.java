package io.github.eutro.wasm2j.events;

import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public interface EventDispatcher<S> {
    <T extends S> void listen(Class<T> eventClass, @NotNull Consumer<T> listener);
}

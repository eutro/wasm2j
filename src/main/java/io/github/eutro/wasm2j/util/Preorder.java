package io.github.eutro.wasm2j.util;

import org.jetbrains.annotations.NotNull;

import java.util.*;

public class Preorder<T> implements Iterable<T> {
    final T root;
    final F<T, ? extends Iterable<T>> f;

    public Preorder(T root, F<T, ? extends Iterable<T>> f) {
        this.root = root;
        this.f = f;
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return new Iter();
    }

    private class Iter implements Iterator<T> {
        private final List<T> stack = new ArrayList<>();
        private final Set<T> seen = new HashSet<>();

        {
            stack.add(root);
            seen.add(root);
        }

        @Override
        public boolean hasNext() {
            return !stack.isEmpty();
        }

        @Override
        public T next() {
            T top = stack.remove(stack.size() - 1);
            for (T next : f.apply(top)) {
                if (seen.add(next)) {
                    stack.add(next);
                }
            }
            return top;
        }
    }
}

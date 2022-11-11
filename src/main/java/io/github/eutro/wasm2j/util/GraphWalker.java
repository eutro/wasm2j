package io.github.eutro.wasm2j.util;

import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Function;

import java.util.*;

public class GraphWalker<T> {
    final T root;
    final F<T, ? extends Iterable<T>> getChildren;

    public GraphWalker(T root, F<T, ? extends Iterable<T>> getChildren) {
        this.root = root;
        this.getChildren = getChildren;
    }

    public static GraphWalker<BasicBlock> blockWalker(BasicBlock root, boolean reverseBlocks) {
        return new GraphWalker<>(root, reverseBlocks
                ? $ -> reversedIterable($.getControl().targets)
                : $ -> $.getControl().targets);
    }

    private static <T> Iterable<T> reversedIterable(List<T> ts) {
        return () -> new Iterator<T>() {
            private final ListIterator<T> li = ts.listIterator(ts.size());

            @Override
            public boolean hasNext() {
                return li.hasPrevious();
            }

            @Override
            public T next() {
                return li.previous();
            }
        };
    }

    public static GraphWalker<BasicBlock> blockWalker(Function func, boolean reverseBlocks) {
        return blockWalker(func.blocks.get(0), reverseBlocks);
    }

    public static GraphWalker<BasicBlock> blockWalker(Function func) {
        return blockWalker(func, false);
    }

    public interface Order<T> extends Iterable<T> {
        default List<T> toList() {
            List<T> ls = new ArrayList<>();
            for (T t : this) {
                ls.add(t);
            }
            return ls;
        }
    }

    public Order<T> preOrder() {
        return PreIter::new;
    }

    public Order<T> postOrder() {
        return PostIter::new;
    }

    private class PreIter implements Iterator<T> {
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
            for (T next : getChildren.apply(top)) {
                if (seen.add(next)) {
                    stack.add(next);
                }
            }
            return top;
        }
    }

    private class PostIter implements Iterator<T> {
        @SuppressWarnings("unchecked")
        private final T sentinel = (T) new Object();
        private final Deque<T> stack = new ArrayDeque<>();
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
            while (true) {
                T last = stack.getLast();
                if (last == sentinel) {
                    stack.removeLast();
                    return stack.removeLast();
                } else {
                    stack.addLast(sentinel);
                    for (T next : getChildren.apply(last)) {
                        if (seen.add(next)) {
                            stack.addLast(next);
                        }
                    }
                }
            }
        }
    }
}

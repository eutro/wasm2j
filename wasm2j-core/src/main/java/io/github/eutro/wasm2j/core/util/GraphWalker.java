package io.github.eutro.wasm2j.core.util;

import io.github.eutro.wasm2j.core.ssa.BasicBlock;
import io.github.eutro.wasm2j.core.ssa.Function;

import java.util.*;

// TODO tests

/**
 * A class for walking a graph depth-first, in pre- or post-order.
 *
 * @param <T> The type of a node in the graph.
 */
public class GraphWalker<T> {
    /**
     * The root of the walk.
     */
    final T root;
    /**
     * The successor function.
     */
    final F<? super T, ? extends Iterable<? extends T>> getChildren;

    /**
     * Construct a graph walker from a root node and a successor function.
     * <p>
     * Elements yielded later by the successor function will be visited first.
     *
     * @param root        The root of the graph to walk from.
     * @param getChildren The successor function of the graph.
     */
    public GraphWalker(T root, F<? super T, ? extends Iterable<? extends T>> getChildren) {
        this.root = root;
        this.getChildren = getChildren;
    }

    /**
     * Create a graph walker over {@link BasicBlock}s.
     *
     * @param root          The root block.
     * @param reverseBlocks If true, jump targets are iterated in reverse order (i.e. if true, first target is visited first).
     * @return The graph walker.
     */
    public static GraphWalker<BasicBlock> blockWalker(BasicBlock root, boolean reverseBlocks) {
        return new GraphWalker<>(root, reverseBlocks
                ? $ -> reversedIterable($.getControl().targets)
                : $ -> $.getControl().targets);
    }

    /**
     * Create a graph walker over the basic blocks in a {@link Function}.
     *
     * @param func          The function whose blocks should be iterated over.
     * @param reverseBlocks If true, jump targets are iterated in reverse order (i.e. if true, first target is visited first).
     * @return The graph walker.
     */
    public static GraphWalker<BasicBlock> blockWalker(Function func, boolean reverseBlocks) {
        return blockWalker(func.blocks.get(0), reverseBlocks);
    }

    /**
     * Creates a graph walker over the basic blocks in a {@link Function}.
     * <p>
     * The last target of each jump will be visited first.
     *
     * @param func The function whose blocks should be iterated over.
     * @return The graph walker.
     */
    public static GraphWalker<BasicBlock> blockWalker(Function func) {
        return blockWalker(func, false);
    }

    /**
     * Get an iterable that iterates over the list in reverse, using its {@link List#listIterator()}).
     *
     * @param ts  The list.
     * @param <T> The type of elements in the list.
     * @return The iterable.
     */
    private static <T> Iterable<T> reversedIterable(List<T> ts) {
        return () -> {
            ListIterator<T> li = ts.listIterator(ts.size());
            return new Iterator<T>() {
                @Override
                public boolean hasNext() {
                    return li.hasPrevious();
                }

                @Override
                public T next() {
                    return li.previous();
                }
            };
        };
    }

    /**
     * An order over a graph.
     *
     * @param <T> The type of each node.
     */
    public interface Order<T> extends Iterable<T> {
        /**
         * Collect this order to a list.
         *
         * @return The elements of the graph, in this order.
         */
        default List<T> toList() {
            List<T> ls = new ArrayList<>();
            for (T t : this) {
                ls.add(t);
            }
            return ls;
        }
    }

    /**
     * Get the pre-order traversal of the graph.
     *
     * @return The pre-order.
     */
    public Order<T> preOrder() {
        return PreIter::new;
    }

    /**
     * Get the post-order traversal of the graph.
     *
     * @return The post-order.
     */
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

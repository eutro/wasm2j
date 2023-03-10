package io.github.eutro.wasm2j.ext;

import java.util.*;

public abstract class TrackedList<E> extends AbstractList<E> implements RandomAccess /* probably */ {
    private List<E> viewed;

    public TrackedList(List<E> viewed) {
        this.viewed = viewed;
    }

    public void setViewed(List<E> viewed) {
        if (this.viewed != null) {
            for (E e : this.viewed) {
                onRemoved(e);
            }
        }
        this.viewed = viewed;
        if (this.viewed != null) {
            for (E e : this.viewed) {
                onAdded(e);
            }
        }
    }

    protected abstract void onAdded(E elt);

    protected abstract void onRemoved(E elt);

    @Override
    public E get(int index) {
        return viewed.get(index);
    }

    @Override
    public int size() {
        return viewed.size();
    }

    @Override
    public boolean add(E e) {
        onAdded(e);
        return viewed.add(e);
    }

    @Override
    public E set(int index, E element) {
        E removed = viewed.set(index, element);
        onRemoved(removed);
        onAdded(element);
        return removed;
    }

    @Override
    public void add(int index, E element) {
        onAdded(element);
        viewed.add(index, element);
    }

    @Override
    public E remove(int index) {
        E removed = viewed.remove(index);
        onRemoved(removed);
        return removed;
    }

    @Override
    public void clear() {
        for (E e : viewed) {
            onRemoved(e);
        }
        viewed.clear();
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        for (E e : c) {
            onAdded(e);
        }
        return viewed.addAll(index, c);
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        ListIterator<E> li = viewed.listIterator(index);
        return new ListIterator<E>() {
            E last;

            @Override
            public boolean hasNext() {
                return li.hasNext();
            }

            @Override
            public E next() {
                return last = li.next();
            }

            @Override
            public boolean hasPrevious() {
                return li.hasPrevious();
            }

            @Override
            public E previous() {
                return last = li.previous();
            }

            @Override
            public int nextIndex() {
                return li.nextIndex();
            }

            @Override
            public int previousIndex() {
                return li.previousIndex();
            }

            @Override
            public void remove() {
                li.remove();
                onRemoved(last);
                last = null;
            }

            @Override
            public void set(E e) {
                li.set(e);
                onRemoved(last);
                onAdded(e);
                last = null;
            }

            @Override
            public void add(E e) {
                li.add(e);
                onAdded(e);
            }
        };
    }
}

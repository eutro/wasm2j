package io.github.eutro.wasm2j.ext;

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

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
}

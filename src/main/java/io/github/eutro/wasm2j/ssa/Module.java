package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.ExtHolder;
import org.jetbrains.annotations.NotNull;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

public final class Module extends ExtHolder {
    public Set<Function> functions = new AbstractSet<Function>() {
        private final Set<Function> delegate = new LinkedHashSet<>();

        @Override
        public boolean contains(Object o) {
            return delegate.contains(o);
        }

        @Override
        public boolean add(Function function) {
            function.attachExt(CommonExts.OWNING_MODULE, Module.this);
            return delegate.add(function);
        }

        @Override
        public boolean remove(Object o) {
            if (delegate.remove(o)) {
                ((Function) o).removeExt(CommonExts.OWNING_MODULE);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public int size() {
            return delegate.size();
        }

        @Override
        public @NotNull Iterator<Function> iterator() {
            Iterator<Function> it = delegate.iterator();
            return new Iterator<Function>() {
                Function last;

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public Function next() {
                    return last = it.next();
                }

                @Override
                public void remove() {
                    it.remove();
                    last.removeExt(CommonExts.OWNING_FUNCTION);
                }
            };
        }
    };
}

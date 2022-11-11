package io.github.eutro.wasm2j.ext;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ExtHolder implements ExtContainer {
    @Nullable
    private Node root = null;

    @NotNull
    private Node getRoot() {
        if (root == null) {
            root = new Node();
        }
        return root;
    }

    public <T> void attachExt(Ext<T> ext, T value) {
        getRoot().setIndex(ext.id, value);
    }

    public <T> void removeExt(Ext<T> ext) {
        getRoot().setIndex(ext.id, null);
    }

    public <T> Optional<T> getExt(Ext<T> ext) {
        return getRoot().getIndex(ext.id).map(ext.getType()::cast);
    }

    private static class Node {
        long mask = 0;
        Object[] children = null;

        Optional<Object> getIndex(int index) {
            int i = index % Long.SIZE;
            if ((mask & (1L << i)) == 0) {
                return Optional.empty();
            }
            int nextIndex = index / Long.SIZE;

            int u = getU(i);
            Object child = children[u];
            if (child instanceof Node) {
                return ((Node) child).getIndex(nextIndex);
            } else if (nextIndex == 0) {
                return Optional.ofNullable(child);
            } else {
                return Optional.empty();
            }
        }

        void setIndex(int index, Object value) {
            int i = index % Long.SIZE;
            int nextIndex = index / Long.SIZE;

            int u = getU(i);
            if ((mask & (1L << i)) == 0) {
                mask |= (1L << i);
                Object[] newChildren = new Object[Long.bitCount(mask)];
                if (children != null) {
                    System.arraycopy(
                            children, 0,
                            newChildren, 0,
                            u
                    );
                    System.arraycopy(
                            children, u,
                            newChildren, u + 1,
                            children.length - u
                    );
                }
                children = newChildren;
            }

            Object child = children[u];
            if (child instanceof Node) {
                ((Node) child).setIndex(nextIndex, value);
            } else if (nextIndex == 0) {
                children[u] = value;
            } else {
                Node newNode = new Node();
                if (child != null) {
                    newNode.setIndex(0, child);
                }
                newNode.setIndex(nextIndex, value);
                children[u] = newNode;
            }
        }

        private int getU(int i) {
            return Long.bitCount((mask >>> i) & ~1);
        }

        @Override
        public String toString() {
            return String.format(
                    "0b%8s_%8s_%8s_%8s_%8s_%8s_%8s_%8s",
                    Long.toBinaryString(0xFF & (mask >>> 56)),
                    Long.toBinaryString(0xFF & (mask >>> 48)),
                    Long.toBinaryString(0xFF & (mask >>> 40)),
                    Long.toBinaryString(0xFF & (mask >>> 32)),
                    Long.toBinaryString(0xFF & (mask >>> 24)),
                    Long.toBinaryString(0xFF & (mask >>> 16)),
                    Long.toBinaryString(0xFF & (mask >>> 8)),
                    Long.toBinaryString(0xFF & (mask))
            ).replace(' ', '0');
        }
    }
}

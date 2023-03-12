package io.github.eutro.wasm2j.ext;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;

/**
 * An implementation of {@link ExtContainer} using a {@link Map}.
 */
@SuppressWarnings("CommentedOutCode")
public class ExtHolder implements ExtContainer {
    @Nullable
    private Map<Ext<?>, Object> map = null; // many ExtHolders don't need it, so don't allocate it!

    @NotNull
    private Map<Ext<?>, Object> getMap() {
        if (map == null) {
            map = new TreeMap<>();
        }
        return map;
    }

    @Override
    public <T> void attachExt(Ext<T> ext, T value) {
        getMap().put(ext, value);
    }

    @Override
    public <T> void removeExt(Ext<T> ext) {
        if (map == null) return;
        Map<Ext<?>, Object> map = this.map;
        map.remove(ext);
        if (map.isEmpty()) {
            this.map = null;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> @Nullable T getNullable(Ext<T> ext) {
        if (map == null) return null;
        return (T) getMap().get(ext);
    }

    // Code for finding the average size of an ExtHolder
    // Running on SpecTest we got:
    // Average ExtHolder size: 1.394200512923116
    // All loads: {0=106447, 1=187380, 2=67210, 3=27637, 4=47303, 5=3839, 6=16}
    /*
    private static List<ExtHolder> ehs = new ArrayList<>();

    {
        ehs.add(this);
    }

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            double totalSize = 0;
            Map<Integer, Integer> totals = ehs.stream().collect(Collectors.toMap(it -> it.map == null ? 0 : it.map.size(), $ -> 1, Integer::sum));
            for (ExtHolder eh : ehs) {
                if (eh.map != null) totalSize += eh.map.size();
            }
            double average = totalSize / ehs.size();
            System.out.println("Average ExtHolder size: " + average);
            System.out.println("All loads: " + totals);
        }));
    }
    */
}

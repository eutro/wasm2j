package io.github.eutro.wasm2j.test;

import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ext.ExtHolder;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.Consumer;

public class ExtHolderBenchTest {

    public static final int OUTER_ITER_COUNT = 1000;
    public static final int INNER_ITER_COUNT = 1000;

    private static final List<Ext<Object>> EXTS = Arrays.asList(
            Ext.create(Object.class, "a"),
            Ext.create(Object.class, "b"),
            Ext.create(Object.class, "c"),
            Ext.create(Object.class, "d"),
            Ext.create(Object.class, "e"),
            Ext.create(Object.class, "f"),
            Ext.create(Object.class, "g"),
            Ext.create(Object.class, "h"),
            Ext.create(Object.class, "i"),
            Ext.create(Object.class, "j")
    );

    void testIt(Consumer<Ext<?>> cnsm) {
        for (int i = 0; i < OUTER_ITER_COUNT; i++) {
            for (int j = 0; j < INNER_ITER_COUNT; j++) {
                cnsm.accept(EXTS.get(j % EXTS.size()));
            }
        }
    }

    @Test
    void testExts() {
        ExtHolder eh = new ExtHolder();
        for (Ext<Object> ext : EXTS) {
            eh.attachExt(ext, new Object());
        }
        testIt(eh::getNullable);
    }

    @Test
    void testHashMap() {
        Map<Ext<Object>, Object> map = new HashMap<>();
        for (Ext<Object> ext : EXTS) {
            map.put(ext, new Object());
        }
        testIt(map::get);
    }

    @Test
    void testTreeMap() {
        Map<Ext<Object>, Object> map = new TreeMap<>();
        for (Ext<Object> ext : EXTS) {
            map.put(ext, new Object());
        }
        testIt(map::get);
    }
}

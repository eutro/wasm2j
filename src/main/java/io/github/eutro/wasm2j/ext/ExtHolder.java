package io.github.eutro.wasm2j.ext;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ExtHolder implements ExtContainer {
    private final Map<Ext<?>, Object> capabilityMap = new HashMap<>();

    public <T> void attachExt(Ext<T> ext, T value) {
        capabilityMap.put(ext, value);
    }

    public <T> void removeExt(Ext<T> ext) {
        capabilityMap.remove(ext);
    }

    public <T> Optional<T> getExt(Ext<T> ext) {
        return Optional.ofNullable(ext.getType().cast(capabilityMap.get(ext)));
    }
}

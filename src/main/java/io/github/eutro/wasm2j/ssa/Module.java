package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.ExtHolder;

import java.util.ArrayList;
import java.util.List;

public final class Module extends ExtHolder {
    public List<Function> funcions = new ArrayList<>();
}

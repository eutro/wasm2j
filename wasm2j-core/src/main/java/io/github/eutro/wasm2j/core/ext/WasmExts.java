package io.github.eutro.wasm2j.core.ext;

import io.github.eutro.jwasm.tree.ExprNode;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.jwasm.tree.TypeNode;
import io.github.eutro.wasm2j.core.ssa.Function;
import io.github.eutro.wasm2j.core.util.Lazy;

import java.util.Map;

public class WasmExts {
    public static final Ext<ModuleNode> MODULE = Ext.create(ModuleNode.class, "MODULE");
    public static final Ext<Map<ExprNode, Lazy<Function>>> FUNC_MAP = Ext.create(Map.class, "FUNC_MAP");
    public static final Ext<TypeNode> TYPE = Ext.create(TypeNode.class, "TYPE");
}

package io.github.eutro.wasm2j.ext;

import io.github.eutro.jwasm.tree.ExprNode;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.jwasm.tree.TypeNode;
import io.github.eutro.wasm2j.ssa.Function;

import java.util.Map;

public class WasmExts {
    public static final Ext<ModuleNode> MODULE = Ext.create(ModuleNode.class);
    public static final Ext<Map<ExprNode, Function>> FUNC_MAP = Ext.create(Map.class);
    public static final Ext<TypeNode> TYPE = Ext.create(TypeNode.class);
}
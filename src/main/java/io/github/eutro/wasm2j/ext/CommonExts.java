package io.github.eutro.wasm2j.ext;

import com.sun.org.apache.xpath.internal.operations.Bool;
import io.github.eutro.wasm2j.ssa.BasicBlock;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.Function;

import java.util.List;
import java.util.Set;

public class CommonExts {
    public static final Ext<BasicBlock> IDOM = Ext.create(BasicBlock.class);
    public static final Ext<List<BasicBlock>> PREDS = Ext.create(List.class);
    public static final Ext<Set<BasicBlock>> DOM_FRONTIER = Ext.create(Set.class);

    public static final Ext<Boolean> IS_PURE = Ext.create(Boolean.class);
    public static final Ext<Boolean> PHI_LOWERED = Ext.create(Boolean.class);

    public static final Ext<Effect> ASSIGNED_AT = Ext.create(Effect.class);

    public static final Ext<Function> OWNING_FUNCTION = Ext.create(Function.class);
    public static final Ext<BasicBlock> OWNING_BLOCK = Ext.create(BasicBlock.class);
    public static final Ext<Effect> OWNING_EFFECT = Ext.create(Effect.class);

    public static final class CodeType {
        public static final CodeType WASM = new CodeType("wasm");
        public static final CodeType JAVA = new CodeType("java");

        public final String name;

        public CodeType(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final Ext<CodeType> CODE_TYPE = Ext.create(CodeType.class);
}

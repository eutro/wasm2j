package io.github.eutro.wasm2j.ext;

import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.ssa.Module;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CommonExts {
    public static final Ext<MetadataState> METADATA_STATE = Ext.create(MetadataState.class);

    public static final Ext<BasicBlock> IDOM = Ext.create(BasicBlock.class);
    public static final Ext<List<BasicBlock>> PREDS = Ext.create(List.class);
    public static final Ext<Set<BasicBlock>> DOM_FRONTIER = Ext.create(Set.class);

    public static final Ext<Boolean> IS_PURE = Ext.create(Boolean.class);
    public static final Ext<Boolean> IS_TRIVIAL = Ext.create(Boolean.class);
    public static final Ext<Boolean> STACKIFIED = Ext.create(Boolean.class);
    public static final Ext<Boolean> IS_PHI = Ext.create(Boolean.class);

    public static final Ext<Effect> ASSIGNED_AT = Ext.create(Effect.class);
    public static final Ext<Set<Insn>> USED_AT = Ext.create(Set.class);

    public static final Ext<Module> OWNING_MODULE = Ext.create(Module.class);
    public static final Ext<Function> OWNING_FUNCTION = Ext.create(Function.class);
    public static final Ext<BasicBlock> OWNING_BLOCK = Ext.create(BasicBlock.class);
    public static final Ext<Effect> OWNING_EFFECT = Ext.create(Effect.class);

    public static final Ext<CodeType> CODE_TYPE = Ext.create(CodeType.class);

    public static final Ext<LiveData> LIVE_DATA = Ext.create(LiveData.class);

    public static <T extends ExtContainer> T markPure(T t) {
        t.attachExt(IS_PURE, true);
        return t;
    }

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

    public static class LiveData {
        public final Set<Var>
                gen = new HashSet<>(),
                kill = new HashSet<>(),
                liveIn = new LinkedHashSet<>(),
                liveOut = new HashSet<>();
    }
}

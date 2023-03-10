package io.github.eutro.wasm2j.ext;

import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.util.F;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CommonExts {
    public static final Ext<MetadataState> METADATA_STATE = Ext.create(MetadataState.class, "METADATA_STATE");

    public static final Ext<BasicBlock> IDOM = Ext.create(BasicBlock.class, "IDOM");
    public static final Ext<List<BasicBlock>> PREDS = Ext.create(List.class, "PREDS");
    public static final Ext<Set<BasicBlock>> DOM_FRONTIER = Ext.create(Set.class, "DOM_FRONTIER");

    public static final Ext<Boolean> IS_PURE = Ext.create(Boolean.class, "IS_PURE");
    public static final Ext<Boolean> IS_TRIVIAL = Ext.create(Boolean.class, "IS_TRIVIAL");

    public static final Object CONSTANT_NULL_SENTINEL = new Object();

    public static Object fillNull(Object obj) {
        return obj == null ? CONSTANT_NULL_SENTINEL : obj;
    }

    public static Object takeNull(Object obj) {
        return obj == CONSTANT_NULL_SENTINEL ? null : obj;
    }

    public static final Ext<Object> CONSTANT_VALUE = Ext.create(Object.class, "CONSTANT_VALUE");

    public static final Ext<F<Insn, Insn>> CONSTANT_PROPAGATOR = Ext.create(F.class, "CONSTANT_PROPAGATOR");

    public static final Ext<Boolean> STACKIFIED = Ext.create(Boolean.class, "STACKIFIED");
    public static final Ext<Boolean> IS_PHI = Ext.create(Boolean.class, "IS_PHI");

    public static final Ext<Effect> ASSIGNED_AT = Ext.create(Effect.class, "ASSIGNED_AT");
    public static final Ext<Set<Insn>> USED_AT = Ext.create(Set.class, "USED_AT");

    public static final Ext<Module> OWNING_MODULE = Ext.create(Module.class, "OWNING_MODULE");
    public static final Ext<Function> OWNING_FUNCTION = Ext.create(Function.class, "OWNING_FUNCTION");
    public static final Ext<BasicBlock> OWNING_BLOCK = Ext.create(BasicBlock.class, "OWNING_BLOCK");
    public static final Ext<Control> OWNING_CONTROL = Ext.create(Control.class, "OWNING_CONTROL");
    public static final Ext<Effect> OWNING_EFFECT = Ext.create(Effect.class, "OWNING_EFFECT");

    public static final Ext<CodeType> CODE_TYPE = Ext.create(CodeType.class, "CODE_TYPE");

    public static final Ext<LiveData> LIVE_DATA = Ext.create(LiveData.class, "LIVE_DATA");

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

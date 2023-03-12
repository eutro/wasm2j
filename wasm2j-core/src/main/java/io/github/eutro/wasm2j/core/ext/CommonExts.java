package io.github.eutro.wasm2j.core.ext;

import io.github.eutro.wasm2j.core.ops.Op;
import io.github.eutro.wasm2j.core.ops.OpKey;
import io.github.eutro.wasm2j.core.passes.form.LowerPhis;
import io.github.eutro.wasm2j.core.passes.meta.*;
import io.github.eutro.wasm2j.core.passes.opts.PropagateConstants;
import io.github.eutro.wasm2j.core.passes.opts.Stackify;
import io.github.eutro.wasm2j.core.ssa.*;
import io.github.eutro.wasm2j.core.util.F;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A collection of {@link Ext}s that exist in both WebAssembly and Java IR.
 */
public class CommonExts {
    /**
     * Attached to a {@link Function}. Has metadata about the form of the function.
     *
     * @see MetadataState
     */
    public static final Ext<MetadataState> METADATA_STATE = Ext.create(MetadataState.class, "METADATA_STATE");

    /**
     * Attached to a {@link BasicBlock}.
     * The <a href="https://en.wikipedia.org/wiki/Dominator_(graph_theory)">immediate dominator</a> of the block.
     * <p>
     * Computed by {@link ComputeDoms}.
     */
    public static final Ext<BasicBlock> IDOM = Ext.create(BasicBlock.class, "IDOM");
    /**
     * Attached to a {@link BasicBlock}. The predecessors of the block.
     * <p>
     * Computed by {@link ComputePreds} or {@link ComputeDoms}.
     */
    public static final Ext<List<BasicBlock>> PREDS = Ext.create(List.class, "PREDS");
    /**
     * Attached to a {@link BasicBlock}.
     * The <a href="https://en.wikipedia.org/wiki/Dominator_(graph_theory)">dominance frontier</a> of the block.
     * <p>
     * Computed by {@link ComputeDomFrontier}.
     */
    public static final Ext<Set<BasicBlock>> DOM_FRONTIER = Ext.create(Set.class, "DOM_FRONTIER");

    /**
     * Attached to an {@link Insn}, {@link Op} or {@link OpKey}.
     * Whether the instruction has no observable side effects.
     */
    public static final Ext<Boolean> IS_PURE = Ext.create(Boolean.class, "IS_PURE");
    /**
     * Attached to an {@link Insn}, {@link Op} or {@link OpKey}.
     * Whether the instruction can be trivially duplicated.
     */
    public static final Ext<Boolean> IS_TRIVIAL = Ext.create(Boolean.class, "IS_TRIVIAL");

    /**
     * Attached to a {@link Var}, computed by {@link PropagateConstants}.
     * The constant value of the variable.
     */
    public static final Ext<Object> CONSTANT_VALUE = Ext.create(Object.class, "CONSTANT_VALUE");

    /**
     * An object that is a sentinel for null for {@link #CONSTANT_VALUE}.
     */
    public static final Object CONSTANT_NULL_SENTINEL = new Object();

    /**
     * If {@code obj} is null, return {@link #CONSTANT_NULL_SENTINEL}, otherwise {@code obj}.
     *
     * @param obj The object.
     * @return {@code obj}, or {@link #CONSTANT_NULL_SENTINEL} if it is null.
     */
    public static Object fillNull(Object obj) {
        return obj == null ? CONSTANT_NULL_SENTINEL : obj;
    }

    /**
     * If {@code obj} is {@link #CONSTANT_NULL_SENTINEL}, returns null, otherwise {@code obj}.
     *
     * @param obj The object.
     * @return {@code obj}, or {@code null} if it is {@link #CONSTANT_NULL_SENTINEL}.
     */
    public static Object takeNull(Object obj) {
        return obj == CONSTANT_NULL_SENTINEL ? null : obj;
    }

    /**
     * Attached to an {@link Insn}, {@link Op} or {@link OpKey}.
     * A function that may process constant arguments, folding it if possible.
     */
    public static final Ext<F<Insn, Insn>> CONSTANT_PROPAGATOR = Ext.create(F.class, "CONSTANT_PROPAGATOR");

    /**
     * Attached to a {@link Var}. Whether this variable has been arranged
     * so that it is passed on the operand stack.
     *
     * @see Stackify
     */
    public static final Ext<Boolean> STACKIFIED = Ext.create(Boolean.class, "STACKIFIED");
    /**
     * Attached to a {@link Var}. Whether this variable has been lowered from a phi instruction.
     *
     * @see LowerPhis
     */
    public static final Ext<Boolean> IS_PHI = Ext.create(Boolean.class, "IS_PHI");

    /**
     * Attached to a {@link Var}. The {@link Effect} this was assigned at. This
     * only makes sense if the IR is in SSA form.
     */
    public static final Ext<Effect> ASSIGNED_AT = Ext.create(Effect.class, "ASSIGNED_AT");
    /**
     * Attached to a {@link Var}, computed by {@link ComputeUses}. The set of instructions that use this variable.
     */
    public static final Ext<Set<Insn>> USED_AT = Ext.create(Set.class, "USED_AT");

    /**
     * Attached to a {@link BasicBlock}. The function this basic block is in.
     */
    public static final Ext<Function> OWNING_FUNCTION = Ext.create(Function.class, "OWNING_FUNCTION");
    /**
     * Attached to a {@link Control} or {@link Effect}. The block this instruction is in.
     */
    public static final Ext<BasicBlock> OWNING_BLOCK = Ext.create(BasicBlock.class, "OWNING_BLOCK");
    /**
     * Attached to a {@link Insn}. The control instruction this insn is part of, if any.
     */
    public static final Ext<Control> OWNING_CONTROL = Ext.create(Control.class, "OWNING_CONTROL");
    /**
     * Attached to a {@link Insn}. The effect instruction this insn is part of, if any.
     */
    public static final Ext<Effect> OWNING_EFFECT = Ext.create(Effect.class, "OWNING_EFFECT");

    /**
     * Attached to a {@link BasicBlock}, computed by {@link ComputeLiveVars}. The live variable information of the block.
     */
    public static final Ext<LiveData> LIVE_DATA = Ext.create(LiveData.class, "LIVE_DATA");

    /**
     * Mark something as pure, by attaching {@link #IS_PURE} {@code = true} to it.
     *
     * @param t   The thing to mark as pure.
     * @param <T> The type of {@code t}.
     * @return {@code t}.
     */
    public static <T extends ExtContainer> T markPure(T t) {
        t.attachExt(IS_PURE, true);
        return t;
    }

    /**
     * The live variable information of a basic block.
     */
    public static class LiveData {
        /**
         * The set of variables used in the block before assignment.
         */
        public final Set<Var> gen = new HashSet<>();
        /**
         * The set of variables that are assigned to in this block.
         */
        public final Set<Var> kill = new HashSet<>();
        /**
         * The set of variables that are alive at the start of the block.
         */
        public final Set<Var> liveIn = new LinkedHashSet<>();
        /**
         * The set of variables that are alive at the end of the block.
         */
        public final Set<Var> liveOut = new HashSet<>();
    }
}

package io.github.eutro.wasm2j.core.ext;

import io.github.eutro.wasm2j.core.passes.IRPass;
import io.github.eutro.wasm2j.core.passes.form.LowerIntrinsics;
import io.github.eutro.wasm2j.core.passes.form.LowerPhis;
import io.github.eutro.wasm2j.core.passes.form.SSAify;
import io.github.eutro.wasm2j.core.passes.meta.*;
import io.github.eutro.wasm2j.core.passes.opts.Stackify;
import io.github.eutro.wasm2j.core.ssa.Function;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Keeps track of the state of certain metadata in a {@link Function}, such as its SSA-ness.
 */
public class MetadataState {
    /**
     * A kind of metadata whose presence can be checked on {@link MetadataState}.
     */
    public static class MetaKind {
        private static final AtomicInteger COUNTER = new AtomicInteger();
        final int id = COUNTER.getAndIncrement();
        final String name;

        MetaKind(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return id;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * A kind of metadata whose presence can be checked, but also specifies how to compute it.
     *
     * @param <T> The IR on which passes must be run to compute the metadata.
     */
    public static class ComputableMetaKind<T> extends MetaKind {
        private final IRPass<T, T>[] passes;

        @SafeVarargs
        ComputableMetaKind(String name, IRPass<T, T>... passes) {
            super(name);
            this.passes = passes;
        }

        void computeFor(T t) {
            for (IRPass<T, T> pass : passes) {
                if (!pass.isInPlace()) throw new IllegalArgumentException();
                pass.run(t);
            }
        }
    }

    /**
     * Metadata that can be computed for functions.
     */
    public static final ComputableMetaKind<Function>
            SSA_FORM = new ComputableMetaKind<>("SSA_FORM", SSAify.INSTANCE),
            PREDS = new ComputableMetaKind<>("PREDS", ComputePreds.INSTANCE),
            DOMS = new ComputableMetaKind<>("DOMS", ComputeDoms.INSTANCE),
            DOM_FRONTIER = new ComputableMetaKind<>("DOM_FRONTIER", ComputeDomFrontier.INSTANCE),
            INTRINSICS_LOWERED = new ComputableMetaKind<>("INTRINSICS_LOWERED",
                    LowerIntrinsics.INSTANCE,
                    LowerPhis.INSTANCE),
            STACKIFIED = new ComputableMetaKind<>("STACKIFIED", Stackify.INSTANCE),
            LIVE_DATA = new ComputableMetaKind<>("LIVE_DATA", ComputeLiveVars.INSTANCE),
            USES = new ComputableMetaKind<>("USES", ComputeUses.INSTANCE),
            JTYPES_INFERRED = new ComputableMetaKind<>("JTYPES_INFERRED", InferTypes.Java.INSTANCE);

    private final BitSet validSet = new BitSet();

    /**
     * Check whether the given metadata is valid.
     *
     * @param kind The kind of metadata.
     * @return Whether it is valid on this.
     */
    public boolean isValid(MetaKind kind) {
        return validSet.get(kind.id);
    }

    /**
     * Check whether the given metadata are valid, and compute it if it is not.
     *
     * @param t     The thing that passes can be run on.
     * @param first The first metadata kind.
     * @param kinds The other metadata kinds.
     * @param <T>   The type of {@code t}.
     */
    @SafeVarargs
    public final <T> void ensureValid(T t, ComputableMetaKind<T> first, ComputableMetaKind<T>... kinds) {
        ensureValid0(t, first);
        for (ComputableMetaKind<T> kind : kinds) {
            ensureValid0(t, kind);
        }
    }

    private <T> void ensureValid0(T t, ComputableMetaKind<T> kind) {
        if (!isValid(kind)) {
            kind.computeFor(t);
            validate(kind);
        }
    }

    /**
     * Mark the given metadata as valid.
     *
     * @param kinds The metadata kinds.
     */
    public void validate(MetaKind... kinds) {
        for (MetaKind kind : kinds) {
            validSet.set(kind.id, true);
        }
    }

    /**
     * Mark the given metadata as invalid.
     *
     * @param kinds The metadata kinds.
     */
    public void invalidate(MetaKind... kinds) {
        for (MetaKind kind : kinds) {
            validSet.set(kind.id, false);
        }
    }

    /**
     * Invalidate everything that becomes invalid if the control flow graph changes.
     */
    public void graphChanged() {
        invalidate(PREDS, DOMS, DOM_FRONTIER);
        varsChanged();
    }

    /**
     * Invalidate everything that becomes invalid if the variables change.
     */
    public void varsChanged() {
        invalidate(LIVE_DATA, USES, JTYPES_INFERRED);
    }
}

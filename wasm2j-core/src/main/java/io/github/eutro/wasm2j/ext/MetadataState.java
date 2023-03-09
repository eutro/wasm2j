package io.github.eutro.wasm2j.ext;

import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.passes.form.LowerIntrinsics;
import io.github.eutro.wasm2j.passes.form.LowerPhis;
import io.github.eutro.wasm2j.passes.form.SSAify;
import io.github.eutro.wasm2j.passes.meta.*;
import io.github.eutro.wasm2j.passes.opts.Stackify;
import io.github.eutro.wasm2j.ssa.Function;

import java.util.BitSet;
import java.util.concurrent.atomic.AtomicInteger;

public class MetadataState {
    public static class MetaKind {
        private static final AtomicInteger COUNTER = new AtomicInteger();
        public final int id = COUNTER.getAndIncrement();
        public final String name;

        private MetaKind(String name) {
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return Integer.hashCode(id);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class ComputableMetaKind<T> extends MetaKind {
        private final IRPass<T, T>[] passes;

        @SafeVarargs
        private ComputableMetaKind(String name, IRPass<T, T>... passes) {
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

    public static final ComputableMetaKind<Function>
            SSA_FORM = new ComputableMetaKind<>("SSA_FORM", SSAify.INSTANCE),
            PREDS = new ComputableMetaKind<>("DOMS", ComputeDoms.INSTANCE),
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

    public boolean isValid(MetaKind kind) {
        return validSet.get(kind.id);
    }

    @Deprecated
    public final void ensureValid(ComputableMetaKind<?> invalid) {
        throw new IllegalStateException();
    }

    @SafeVarargs
    public final <T> void ensureValid(T t, ComputableMetaKind<T>... kinds) {
        for (ComputableMetaKind<T> kind : kinds) {
            if (!isValid(kind)) {
                kind.computeFor(t);
                validate(kind);
            }
        }
    }

    public void validate(MetaKind... kinds) {
        for (MetaKind kind : kinds) {
            validSet.set(kind.id, true);
        }
    }

    public void invalidate(MetaKind... kinds) {
        for (MetaKind kind : kinds) {
            validSet.set(kind.id, false);
        }
    }

    public void graphChanged() {
        invalidate(PREDS, DOMS, DOM_FRONTIER);
        varsChanged();
    }

    public void varsChanged() {
        invalidate(LIVE_DATA, USES, JTYPES_INFERRED);
    }
}

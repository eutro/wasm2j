package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.conf.impl.DelegatingExporter;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.Var;
import io.github.eutro.wasm2j.util.IRUtils;

public interface TableConvention extends ExportableConvention, ConstructorCallback {
    void emitTableRef(IRBuilder ib, Effect effect);

    void emitTableStore(IRBuilder ib, Effect effect);

    void emitTableSize(IRBuilder ib, Effect effect);

    default void emitTableGrow(IRBuilder ib, Effect effect) {
        ib.insert(CommonOps.constant(-1).copyFrom(effect));
    }

    void emitTableInit(IRBuilder ib, Effect effect, Var data);

    default void emitBoundsCheck(IRBuilder ib, int table, Var bound) {
        Var sz = ib.func.newVar("sz");
        emitTableSize(ib, WasmOps.TABLE_SIZE
                .create(table)
                .insn()
                .assignTo(sz));
        IRUtils.trapWhen(ib, JavaOps.BR_COND.create(JavaOps.JumpType.IF_ICMPGT).insn(bound, sz),
                "out of bounds table access");
    }

    default void emitTableCopy(IRBuilder ib, Effect effect, TableConvention dst) {
        IRUtils.emitCopy(ib, effect, WasmOps.TABLE_COPY,
                (table, idx, val) -> emitTableRef(ib, WasmOps.TABLE_REF
                        .create(table)
                        .insn(idx)
                        .assignTo(val)),
                (table, idx, val) -> dst.emitTableStore(ib, WasmOps.TABLE_STORE
                        .create(table)
                        .insn(idx, val)
                        .assignTo()),
                this, dst,
                TableConvention::emitBoundsCheck
        );
    }

    default void emitTableFill(IRBuilder ib, Effect effect) {
        IRUtils.emitFill(ib, effect, WasmOps.TABLE_FILL, (table, idx, val) ->
                        emitTableStore(ib, WasmOps.TABLE_STORE
                                .create(table)
                                .insn(idx, val)
                                .assignTo()),
                this,
                TableConvention::emitBoundsCheck
        );
    }

    class Delegating extends DelegatingExporter implements TableConvention {
        protected final TableConvention delegate;

        public Delegating(TableConvention delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        public void emitTableRef(IRBuilder ib, Effect effect) {
            delegate.emitTableRef(ib, effect);
        }

        @Override
        public void emitTableStore(IRBuilder ib, Effect effect) {
            delegate.emitTableStore(ib, effect);
        }

        @Override
        public void emitTableSize(IRBuilder ib, Effect effect) {
            delegate.emitTableSize(ib, effect);
        }

        @Override
        public void emitTableGrow(IRBuilder ib, Effect effect) {
            delegate.emitTableGrow(ib, effect);
        }

        @Override
        public void emitTableInit(IRBuilder ib, Effect effect, Var data) {
            delegate.emitTableInit(ib, effect, data);
        }

        @Override
        public void emitTableCopy(IRBuilder ib, Effect effect, TableConvention dst) {
            delegate.emitTableCopy(ib, effect, dst);
        }

        @Override
        public void emitTableFill(IRBuilder ib, Effect effect) {
            delegate.emitTableFill(ib, effect);
        }

        @Override
        public void modifyConstructor(IRBuilder ib, JavaExts.JavaMethod ctorMethod, Module module, JavaExts.JavaClass jClass) {
            delegate.modifyConstructor(ib, ctorMethod, module, jClass);
        }
    }
}

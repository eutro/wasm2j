package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.conf.impl.DelegatingExporter;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.Var;
import io.github.eutro.wasm2j.util.IRUtils;
import io.github.eutro.wasm2j.util.Pair;

import java.util.Iterator;

public interface TableConvention extends ExportableConvention, ConstructorCallback {
    void emitTableRef(IRBuilder ib, Effect effect);

    void emitTableStore(IRBuilder ib, Effect effect);

    void emitTableSize(IRBuilder ib, Effect effect);

    default void emitTableGrow(IRBuilder ib, Effect effect) {
        ib.insert(CommonOps.constant(-1).copyFrom(effect));
    }

    void emitTableInit(IRBuilder ib, Effect effect, Var data);

    default void emitTableCopy(IRBuilder ib, Effect effect, TableConvention dst) {
        Pair<Integer, Integer> arg = WasmOps.TABLE_COPY.cast(effect.insn().op).arg;
        int srcIdx = arg.left;
        int dstIdx = arg.right;
        Iterator<Var> iter = effect.insn().args.iterator();
        Var dstAddr = iter.next();
        Var srcAddr = iter.next();
        Var len = iter.next();

        IRUtils.lenLoop(ib, new Var[]{dstAddr, srcAddr}, len, vars -> {
            Var x = ib.func.newVar("x");
            emitTableRef(ib, WasmOps.TABLE_REF
                    .create(srcIdx)
                    .insn(vars[0])
                    .assignTo(x));
            dst.emitTableStore(ib, WasmOps.TABLE_STORE
                    .create(dstIdx)
                    .insn(vars[1], x)
                    .assignTo());
        });
    }

    default void emitTableFill(IRBuilder ib, Effect effect) {
        int table = WasmOps.TABLE_FILL.cast(effect.insn().op).arg;
        Iterator<Var> iter = effect.insn().args.iterator();
        Var idx = iter.next();
        Var value = iter.next();
        Var len = iter.next();

        IRUtils.lenLoop(ib, new Var[]{idx}, len, vars ->
                emitTableStore(ib, WasmOps.TABLE_STORE
                        .create(table)
                        .insn(vars[0], value)
                        .assignTo()));
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

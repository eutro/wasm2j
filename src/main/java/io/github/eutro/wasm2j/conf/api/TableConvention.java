package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.conf.impl.DelegatingExporter;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.Op;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.Var;
import io.github.eutro.wasm2j.util.IRUtils;
import io.github.eutro.wasm2j.util.Pair;
import org.objectweb.asm.tree.InsnNode;

import java.util.Iterator;

import static org.objectweb.asm.Opcodes.IADD;

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
        Pair<Integer, Integer> arg = WasmOps.TABLE_COPY.cast(effect.insn().op).arg;
        int srcTable = arg.left;
        int dstTable = arg.right;
        Iterator<Var> iter = effect.insn().args.iterator();
        Var dstIdx = iter.next();
        Var srcIdx = iter.next();
        Var len = iter.next();

        Op add = JavaOps.insns(new InsnNode(IADD));
        emitBoundsCheck(ib, srcTable, ib.insert(add.insn(dstIdx, len), "dstEnd"));
        emitBoundsCheck(ib, srcTable, ib.insert(add.insn(srcIdx, len), "srcEnd"));
        IRUtils.lenLoop(ib, new Var[]{dstIdx, srcIdx}, len, vars -> {
            Var x = ib.func.newVar("x");
            emitTableRef(ib, WasmOps.TABLE_REF
                    .create(srcTable)
                    .insn(vars[1])
                    .assignTo(x));
            dst.emitTableStore(ib, WasmOps.TABLE_STORE
                    .create(dstTable)
                    .insn(vars[0], x)
                    .assignTo());
        });
    }

    default void emitTableFill(IRBuilder ib, Effect effect) {
        int table = WasmOps.TABLE_FILL.cast(effect.insn().op).arg;
        Iterator<Var> iter = effect.insn().args.iterator();
        Var idx = iter.next();
        Var value = iter.next();
        Var len = iter.next();

        Op add = JavaOps.insns(new InsnNode(IADD));
        emitBoundsCheck(ib, table, ib.insert(add.insn(idx, len), "idxEnd"));
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

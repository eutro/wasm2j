package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.jwasm.Opcodes;
import io.github.eutro.wasm2j.conf.impl.DelegatingExporter;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ops.WasmOps.WithMemArg;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.Var;
import io.github.eutro.wasm2j.util.IRUtils;

import java.util.Iterator;

public interface MemoryConvention extends ExportableConvention, ConstructorCallback {
    void emitMemLoad(IRBuilder ib, Effect effect);

    void emitMemStore(IRBuilder ib, Effect effect);

    void emitMemSize(IRBuilder ib, Effect effect);

    void emitMemGrow(IRBuilder ib, Effect effect);

    void emitMemInit(IRBuilder ib, Effect effect, Var data);

    default void emitMemCopy(IRBuilder ib, Effect effect, MemoryConvention dst) {
        Iterator<Var> iter = effect.insn().args.iterator();
        Var dstAddr = iter.next();
        Var srcAddr = iter.next();
        Var len = iter.next();

        IRUtils.lenLoop(ib, new Var[]{dstAddr, srcAddr}, len, vars -> {
            Var b = ib.func.newVar("b");
            emitMemLoad(ib, WasmOps.MEM_LOAD
                    .create(WithMemArg.create(WasmOps.DerefType.fromOpcode(Opcodes.I32_LOAD8_U), 0))
                    .insn(vars[0])
                    .assignTo(b));
            dst.emitMemStore(ib, WasmOps.MEM_STORE
                    .create(WithMemArg.create(WasmOps.StoreType.I32_8, 0))
                    .insn(vars[1], b)
                    .assignTo());
        });
    }

    default void emitMemFill(IRBuilder ib, Effect effect) {
        Iterator<Var> iter = effect.insn().args.iterator();
        Var idx = iter.next();
        Var value = iter.next();
        Var len = iter.next();

        IRUtils.lenLoop(ib, new Var[]{idx}, len, vars ->
                emitMemStore(ib, WasmOps.MEM_STORE
                        .create(WithMemArg.create(WasmOps.StoreType.I32_8, 0))
                        .insn(vars[0], value)
                        .assignTo()));
    }

    class Delegating extends DelegatingExporter implements MemoryConvention {
        protected final MemoryConvention delegate;

        public Delegating(MemoryConvention delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        public void emitMemLoad(IRBuilder ib, Effect effect) {
            delegate.emitMemLoad(ib, effect);
        }

        @Override
        public void emitMemStore(IRBuilder ib, Effect effect) {
            delegate.emitMemStore(ib, effect);
        }

        @Override
        public void emitMemSize(IRBuilder ib, Effect effect) {
            delegate.emitMemSize(ib, effect);
        }

        @Override
        public void emitMemGrow(IRBuilder ib, Effect effect) {
            delegate.emitMemGrow(ib, effect);
        }

        @Override
        public void emitMemInit(IRBuilder ib, Effect effect, Var data) {
            delegate.emitMemInit(ib, effect, data);
        }

        @Override
        public void emitMemCopy(IRBuilder ib, Effect effect, MemoryConvention dst) {
            delegate.emitMemCopy(ib, effect, dst);
        }

        @Override
        public void emitMemFill(IRBuilder ib, Effect effect) {
            delegate.emitMemFill(ib, effect);
        }

        @Override
        public void modifyConstructor(IRBuilder ib, JavaExts.JavaMethod ctorMethod, Module module, JavaExts.JavaClass jClass) {
            delegate.modifyConstructor(ib, ctorMethod, module, jClass);
        }
    }
}

package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.jwasm.Opcodes;
import io.github.eutro.wasm2j.conf.impl.DelegatingExporter;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.Op;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ops.WasmOps.WithMemArg;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.Var;
import io.github.eutro.wasm2j.util.IRUtils;
import org.objectweb.asm.tree.InsnNode;

import java.util.Iterator;

import static org.objectweb.asm.Opcodes.IADD;
import static org.objectweb.asm.Opcodes.IMUL;

public interface MemoryConvention extends ExportableConvention, ConstructorCallback {
    void emitMemLoad(IRBuilder ib, Effect effect);

    void emitMemStore(IRBuilder ib, Effect effect);

    void emitMemSize(IRBuilder ib, Effect effect);

    void emitMemGrow(IRBuilder ib, Effect effect);

    void emitMemInit(IRBuilder ib, Effect effect, Var data);

    default void emitBoundsCheck(IRBuilder ib, int mem, Var bound) {
        Var sz = ib.func.newVar("sz");
        emitMemSize(ib, WasmOps.MEM_SIZE
                .create(mem)
                .insn()
                .assignTo(sz));
        Var szRaw = ib.insert(JavaOps.insns(new InsnNode(IMUL))
                        .insn(sz, ib.insert(CommonOps.constant(Opcodes.PAGE_SIZE), "pgSz")),
                "szRaw");
        IRUtils.trapWhen(ib, JavaOps.BR_COND.create(JavaOps.JumpType.IF_ICMPGT).insn(bound, szRaw),
                "out of bounds memory access");
    }

    default void emitMemCopy(IRBuilder ib, Effect effect, MemoryConvention dst) {
        int thisIdx = WasmOps.MEM_COPY.cast(effect.insn().op).arg.left;
        Iterator<Var> iter = effect.insn().args.iterator();
        Var dstAddr = iter.next();
        Var srcAddr = iter.next();
        Var len = iter.next();

        Op add = JavaOps.insns(new InsnNode(IADD));
        emitBoundsCheck(ib, thisIdx, ib.insert(add.insn(dstAddr, len), "dstEnd"));
        emitBoundsCheck(ib, thisIdx, ib.insert(add.insn(srcAddr, len), "srcEnd"));
        IRUtils.lenLoop(ib, new Var[]{dstAddr, srcAddr}, len, vars -> {
            Var b = ib.func.newVar("b");
            emitMemLoad(ib, WasmOps.MEM_LOAD
                    .create(WithMemArg.create(WasmOps.DerefType.fromOpcode(Opcodes.I32_LOAD8_U), 0))
                    .insn(vars[1])
                    .assignTo(b));
            dst.emitMemStore(ib, WasmOps.MEM_STORE
                    .create(WithMemArg.create(WasmOps.StoreType.I32_8, 0))
                    .insn(vars[0], b)
                    .assignTo());
        });
    }

    default void emitMemFill(IRBuilder ib, Effect effect) {
        int thisIdx = WasmOps.MEM_FILL.cast(effect.insn().op).arg;
        Iterator<Var> iter = effect.insn().args.iterator();
        Var idx = iter.next();
        Var value = iter.next();
        Var len = iter.next();

        Op add = JavaOps.insns(new InsnNode(IADD));
        emitBoundsCheck(ib, thisIdx, ib.insert(add.insn(idx, len), "idxEnd"));
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

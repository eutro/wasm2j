package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.jwasm.Opcodes;
import io.github.eutro.wasm2j.conf.impl.DelegatingExporter;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ext.ExtContainer;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.util.IRUtils;
import org.objectweb.asm.tree.InsnNode;

import java.nio.ByteBuffer;

import static org.objectweb.asm.Opcodes.LCMP;
import static org.objectweb.asm.Opcodes.LMUL;

/**
 * The convention for a WebAssembly memory, defining how the memory can be accessed and exported, and
 * added to the constructor.
 *
 * @see WirJavaConvention
 */
public interface MemoryConvention extends ExportableConvention, ConstructorCallback, ExtContainer {
    /**
     * Emit code for a WebAssembly {@code *.load} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#MEM_LOAD},<br>
     * with one argument: the (un-offset) memory address;<br>
     * it will assign to one variable: the value loaded from memory.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     */
    void emitMemLoad(IRBuilder ib, Effect effect);

    /**
     * Emit code for a WebAssembly {@code *.store} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#MEM_STORE},<br>
     * with two arguments: the (un-offset) memory address, the value to store;<br>
     * it will assign to no variables.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     */
    void emitMemStore(IRBuilder ib, Effect effect);

    /**
     * Emit code for a WebAssembly {@code memory.size} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#MEM_SIZE},<br>
     * with no arguments;<br>
     * it will assign to one variable: the retrieved memory size (in pages).
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     * @see Opcodes#PAGE_SIZE
     */
    void emitMemSize(IRBuilder ib, Effect effect);

    /**
     * Emit code for a WebAssembly {@code memory.grow} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#MEM_GROW},<br>
     * with one argument: the number of pages to grow by;<br>
     * it will assign to one variable: the previous size (in pages), or {@code -1} if resizing failed.
     * <p>
     * The default implementation returns {@code -1} and ignores the arguments.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     * @see Opcodes#PAGE_SIZE
     */
    void emitMemGrow(IRBuilder ib, Effect effect);

    /**
     * Emit code for a WebAssembly {@code memory.init} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#MEM_INIT},<br>
     * with three arguments: the memory address to start at, the data index to start at, the number of bytes to copy;<br>
     * it will assign to no variables.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     * @param data   The {@link ByteBuffer} containing the data.
     */
    void emitMemInit(IRBuilder ib, Effect effect, Var data);

    /**
     * Emit code which traps if {@code bound}, a memory address, is outside the bounds of this memory.
     * <p>
     * Implementations are encouraged to override this if they can
     * compute their size in bytes quicker than just multiplying (the result of)
     * {@link #emitMemSize(IRBuilder, Effect)} by {@link Opcodes#PAGE_SIZE}.
     *
     * @param ib    The instruction builder.
     * @param mem   The index of this memory.
     * @param bound The bound.
     */
    default void emitBoundsCheck(IRBuilder ib, int mem, Var bound) {
        Var sz = ib.func.newVar("sz");
        emitMemSize(ib, WasmOps.MEM_SIZE
                .create(mem)
                .insn()
                .assignTo(sz));
        sz = ib.insert(JavaOps.I2L.insn(sz), "szL");
        Var szRaw = ib.insert(JavaOps.insns(new InsnNode(LMUL))
                        .insn(sz, ib.insert(CommonOps.constant((long) Opcodes.PAGE_SIZE), "pgSz")),
                "szRaw");
        IRUtils.trapWhen(ib, JavaOps.BR_COND.create(JavaOps.JumpType.IFGT)
                        .insn(ib.insert(JavaOps.insns(new InsnNode(LCMP))
                                        .insn(bound, szRaw),
                                "cmp")),
                "out of bounds memory access");
    }

    /**
     * Emit code for a WebAssembly {@code memory.copy} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#MEM_COPY},<br>
     * with three arguments: the destination address to start at, the source address to start at, the number of bytes to copy;<br>
     * it will assign to no variables.
     * <p>
     * Implementations are strongly encouraged to override the default implementation
     * when they can more efficiently copy within themselves, or to
     * other instances of the same or similar memory conventions.
     * <p>
     * For example, a {@link ByteBuffer}-based implementation would prefer to use
     * {@link ByteBuffer#put(ByteBuffer)},
     * as it is more efficient, and smaller, than the default implementation.
     * <p>
     * {@link Ext}s should be used to find whether implementations
     * are suitably compatible, as they may be obscured by {@link Delegating delegating}
     * implementations.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     * @param dst    The destination table.
     */
    default void emitMemCopy(IRBuilder ib, Effect effect, MemoryConvention dst) {
        IRUtils.emitCopy(ib, effect, WasmOps.MEM_COPY,
                (memory, idx, val) -> emitMemLoad(ib, WasmOps.MEM_LOAD
                        .create(WasmOps.WithMemArg.create(WasmOps.DerefType.fromOpcode(io.github.eutro.jwasm.Opcodes.I32_LOAD8_U), 0))
                        .insn(idx)
                        .assignTo(val)),
                (memory, idx, val) -> dst.emitMemStore(ib, WasmOps.MEM_STORE
                        .create(WasmOps.WithMemArg.create(WasmOps.StoreType.I32_8, 0))
                        .insn(idx, val)
                        .assignTo()),
                this, dst,
                MemoryConvention::emitBoundsCheck
        );
    }

    /**
     * Emit code for a WebAssembly {@code memory.fill} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#MEM_FILL},<br>
     * with three arguments: the destination address to start at, the byte to fill with, the number of bytes to fill;<br>
     * it will assign to no variables.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     */
    default void emitMemFill(IRBuilder ib, Effect effect) {
        IRUtils.emitFill(ib, effect, WasmOps.MEM_FILL, (memory, idx, val) ->
                        emitMemStore(ib, WasmOps.MEM_STORE
                                .create(WasmOps.WithMemArg.create(WasmOps.StoreType.I32_8, 0))
                                .insn(idx, val)
                                .assignTo()),
                this,
                MemoryConvention::emitBoundsCheck
        );
    }

    /**
     * A {@link MemoryConvention} which delegates all calls to another.
     */
    class Delegating extends DelegatingExporter implements MemoryConvention {
        /**
         * The convention calls will be delegated to.
         */
        protected final MemoryConvention delegate;

        /**
         * Construct a new {@link Delegating} with the given delegate.
         *
         * @param delegate The delegate.
         */
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
        public void modifyConstructor(IRBuilder ib, JClass.JavaMethod ctorMethod, Module module, JClass jClass) {
            delegate.modifyConstructor(ib, ctorMethod, module, jClass);
        }
    }
}

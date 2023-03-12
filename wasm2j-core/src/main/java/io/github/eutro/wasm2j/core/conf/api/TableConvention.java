package io.github.eutro.wasm2j.core.conf.api;

import io.github.eutro.wasm2j.core.conf.impl.DelegatingExporter;
import io.github.eutro.wasm2j.core.ext.Ext;
import io.github.eutro.wasm2j.core.ext.ExtContainer;
import io.github.eutro.wasm2j.core.ops.CommonOps;
import io.github.eutro.wasm2j.core.ops.JavaOps;
import io.github.eutro.wasm2j.core.ops.WasmOps;
import io.github.eutro.wasm2j.core.ssa.Module;
import io.github.eutro.wasm2j.core.ssa.*;
import io.github.eutro.wasm2j.core.util.IRUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.InsnNode;

/**
 * The convention for a WebAssembly table, defining how the table can be accessed and exported, and
 * added to the constructor.
 *
 * @see WirJavaConvention
 */
public interface TableConvention extends ExportableConvention, ConstructorCallback, ExtContainer {
    /**
     * Emit code for a WebAssembly {@code table.get} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#TABLE_REF},<br>
     * with one argument: the index in the table;<br>
     * it will assign to one variable: the retrieved table element.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     */
    void emitTableRef(IRBuilder ib, Effect effect);

    /**
     * Emit code for a WebAssembly {@code table.set} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#TABLE_STORE},<br>
     * with two arguments: the index in the table, the element to set it to;<br>
     * it will assign to no variables.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     */
    void emitTableStore(IRBuilder ib, Effect effect);

    /**
     * Emit code for a WebAssembly {@code table.size} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#TABLE_SIZE},<br>
     * with no arguments;<br>
     * it will assign to one variable: the retrieved table size.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     */
    void emitTableSize(IRBuilder ib, Effect effect);

    /**
     * Emit code for a WebAssembly {@code table.grow} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#TABLE_GROW},<br>
     * with two arguments: the number of elements to grow by, the element to fill with;<br>
     * it will assign to one variable: the previous size, or {@code -1} if resizing failed.
     * <p>
     * The default implementation returns {@code -1} and ignores the arguments.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     */
    default void emitTableGrow(IRBuilder ib, Effect effect) {
        ib.insert(CommonOps.constant(-1).copyFrom(effect));
    }

    /**
     * Emit code for a WebAssembly {@code table.init} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#TABLE_INIT},<br>
     * with three arguments: the table index to start at, the elem index to start at, the number of elements to copy;<br>
     * it will assign to no variables.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     * @param data   The array containing the data.
     */
    void emitTableInit(IRBuilder ib, Effect effect, Var data);

    /**
     * Emit code which traps if {@code bound} is outside the bounds of this table.
     *
     * @param ib    The instruction builder.
     * @param table The index of this table.
     * @param bound The bound.
     */
    default void emitBoundsCheck(IRBuilder ib, int table, Var bound) {
        Var sz = ib.func.newVar("sz");
        emitTableSize(ib, WasmOps.TABLE_SIZE
                .create(table)
                .insn()
                .assignTo(sz));
        sz = ib.insert(JavaOps.I2L.insn(sz), "szL");
        IRUtils.trapWhen(ib, JavaOps.BR_COND.create(JavaOps.JumpType.IFGT)
                        .insn(ib.insert(JavaOps.insns(new InsnNode(Opcodes.LCMP))
                                        .insn(bound, sz),
                                "cmp")),
                "out of bounds table access");
    }

    /**
     * Emit code for a WebAssembly {@code table.copy} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#TABLE_COPY},<br>
     * with three arguments: the destination index to start at, the source index to start at, the number of elements to copy;<br>
     * it will assign to no variables.
     * <p>
     * Implementations are encouraged to override the default implementation
     * when they can more efficiently copy within themselves, or to
     * other instances of the same or similar table conventions.
     * <p>
     * For example, an array-based implementation would prefer to use
     * {@link System#arraycopy(Object, int, Object, int, int)},
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

    /**
     * Emit code for a WebAssembly {@code table.fill} instruction.
     * <p>
     * The effect will have an instruction of type {@link WasmOps#TABLE_FILL},<br>
     * with three arguments: the destination index to start at, the element to fill with, the number of elements to fill;<br>
     * it will assign to no variables.
     *
     * @param ib     The instruction builder.
     * @param effect The effect.
     */
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

    /**
     * A {@link TableConvention} which delegates all calls to another.
     */
    class Delegating extends DelegatingExporter implements TableConvention {
        /**
         * The convention calls will be delegated to.
         */
        protected final TableConvention delegate;

        /**
         * Construct a new {@link Delegating} with the given delegate.
         *
         * @param delegate The delegate.
         */
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
        public void modifyConstructor(IRBuilder ib, JClass.JavaMethod ctorMethod, Module module, JClass jClass) {
            delegate.modifyConstructor(ib, ctorMethod, module, jClass);
        }
    }
}

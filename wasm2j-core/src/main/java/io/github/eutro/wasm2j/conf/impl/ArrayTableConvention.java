package io.github.eutro.wasm2j.conf.impl;

import io.github.eutro.wasm2j.conf.api.ExportableConvention;
import io.github.eutro.wasm2j.conf.api.TableConvention;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.util.ValueGetterSetter;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.Arrays;

/**
 * An {@link TableConvention} that has a Java array as its underlying representation.
 */
public class ArrayTableConvention extends DelegatingExporter implements TableConvention {
    /**
     * An ext that provides access to the underlying array.
     * <p>
     * For use with optimising {@link #emitTableCopy(IRBuilder, Effect, TableConvention)}.
     */
    public static final Ext<ValueGetterSetter> TABLE_ARRAY = Ext.create(ValueGetterSetter.class, "TABLE_ARRAY");

    private static final JClass SYSTEM = JClass.emptyFromJava(System.class);
    private static final JClass.JavaMethod SYSTEM_ARRAYCOPY = SYSTEM.lookupMethod("arraycopy",
            Object.class, int.class, Object.class, int.class, int.class);
    private static final JClass ARRAYS_CLASS = JClass.emptyFromJava(Arrays.class);
    private final ValueGetterSetter table;
    private final Type componentType;
    private final Integer max;

    /**
     * Construct an {@link ArrayTableConvention}.
     *
     * @param exporter      The exporter.
     * @param table         The getter/setter for the underlying array.
     * @param componentType The array component type.
     * @param max           The maximum of the table type.
     */
    public ArrayTableConvention(
            ExportableConvention exporter,
            ValueGetterSetter table,
            Type componentType,
            @Nullable Integer max
    ) {
        super(exporter);
        this.table = table;
        this.componentType = componentType;
        this.max = max;
    }

    @Override
    public void emitTableRef(IRBuilder ib, Effect effect) {
        ib.insert(JavaOps.ARRAY_GET.create()
                .insn(table.get(ib), effect.insn().args().get(0))
                .copyFrom(effect));

    }

    @Override
    public void emitTableStore(IRBuilder ib, Effect effect) {
        ib.insert(JavaOps.ARRAY_SET.create()
                .insn(table.get(ib),
                        effect.insn().args().get(0),
                        effect.insn().args().get(1))
                .assignTo());

    }

    @Override
    public void emitTableSize(IRBuilder ib, Effect effect) {
        ib.insert(JavaOps.insns(new InsnNode(Opcodes.ARRAYLENGTH)).insn(table.get(ib)).copyFrom(effect));
    }

    @SuppressWarnings({"CommentedOutCode", "DuplicatedCode"})
    @Override
    public void emitTableGrow(IRBuilder ib, Effect effect) {
        // This is what we are implementing:
        /*
        int res;
        int sz = tbl.length;
        int newSz = sz + growBy;
        if (growBy < 0 || newSz > max) {
            res = -1;
        } else {
            try {
                Object[] newTbl = Arrays.copyOf(tbl, newSz);
                if (with != null) {
                    Arrays.fill(newTbl, sz, newSz, with);
                }
                tbl = newTbl;
                res = sz;
            } catch (OutOfMemoryError ignored) {
                res = -1;
            }
        }
        return res;
         */

        Type oome = Type.getType(OutOfMemoryError.class);

        Var growBy = effect.insn().args().get(0);
        Var fillWith = effect.insn().args().get(1);

        BasicBlock failBlock = ib.func.newBb();

        BasicBlock k = ib.func.newBb();
        ib.insertCtrl(JavaOps.BR_COND.create(JavaOps.JumpType.IFLT).insn(growBy).jumpsTo(failBlock, k));
        ib.setBlock(k);

        Var tbl = table.get(ib);
        Var sz = ib.insert(JavaOps.insns(new InsnNode(Opcodes.ARRAYLENGTH)).insn(tbl), "sz");
        Var newSz = ib.insert(JavaOps.IADD.insn(growBy, sz), "newSz");
        if (max != null) {
            k = ib.func.newBb();
            ib.insertCtrl(JavaOps.BR_COND.create(JavaOps.JumpType.IF_ICMPGT)
                    .insn(newSz, ib.insert(CommonOps.constant(max), "max"))
                    .jumpsTo(failBlock, k));
            ib.setBlock(k);
        }

        k = ib.func.newBb();
        BasicBlock catchBlock = ib.func.newBb();
        ib.insertCtrl(JavaOps.TRY.create(oome).insn().jumpsTo(catchBlock, k));
        ib.setBlock(k);

        Var newTbl = ib.insert(JavaOps.insns(new TypeInsnNode(Opcodes.CHECKCAST, "[" + componentType.getDescriptor()))
                        .insn(ib.insert(JavaOps.INVOKE
                                        .create(ARRAYS_CLASS.lookupMethod("copyOf", Object[].class, int.class))
                                        .insn(tbl, newSz),
                                "newTblRaw")),
                "newTbl");

        BasicBlock fillBlock = ib.func.newBb();
        k = ib.func.newBb();
        ib.insertCtrl(JavaOps.BR_COND.create(JavaOps.JumpType.IFNULL).insn(fillWith).jumpsTo(k, fillBlock));
        ib.setBlock(fillBlock);
        ib.insert(JavaOps.INVOKE
                .create(ARRAYS_CLASS.lookupMethod("fill", Object[].class, int.class, int.class, Object.class))
                .insn(newTbl, sz, newSz, fillWith)
                .assignTo());
        ib.insertCtrl(Control.br(k));
        ib.setBlock(k);

        BasicBlock endBlock = ib.func.newBb();
        BasicBlock successBlock = ib.getBlock();
        table.set(ib, newTbl);
        ib.insertCtrl(Control.br(endBlock));

        ib.setBlock(catchBlock);
        ib.insert(JavaOps.CATCH.create(oome).insn(), "exn");
        ib.insertCtrl(Control.br(failBlock));

        ib.setBlock(failBlock);
        Var err = ib.insert(CommonOps.constant(-1), "err");
        ib.insertCtrl(Control.br(endBlock));

        ib.setBlock(endBlock);
        ib.insert(CommonOps.PHI.create(Arrays.asList(successBlock, failBlock))
                .insn(sz, err)
                .copyFrom(effect));
    }

    @Override
    public void emitTableInit(IRBuilder ib, Effect effect, Var data) {
        ib.insert(JavaOps.INVOKE
                .create(SYSTEM_ARRAYCOPY)
                .insn(data,
                        effect.insn().args().get(1),
                        table.get(ib),
                        effect.insn().args().get(0),
                        effect.insn().args().get(2))
                .copyFrom(effect));
    }
}

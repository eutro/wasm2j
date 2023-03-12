package io.github.eutro.wasm2j.core.conf.impl;

import io.github.eutro.wasm2j.core.conf.itf.ExportableConvention;
import io.github.eutro.wasm2j.core.conf.itf.MemoryConvention;
import io.github.eutro.wasm2j.core.ext.Ext;
import io.github.eutro.wasm2j.core.ops.CommonOps;
import io.github.eutro.wasm2j.core.ops.JavaOps;
import io.github.eutro.wasm2j.core.ops.Op;
import io.github.eutro.wasm2j.core.ops.WasmOps;
import io.github.eutro.wasm2j.core.ssa.*;
import io.github.eutro.wasm2j.core.util.IRUtils;
import io.github.eutro.wasm2j.core.util.Instructions;
import io.github.eutro.wasm2j.core.util.Pair;
import io.github.eutro.wasm2j.core.util.ValueGetterSetter;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnNode;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;

import static io.github.eutro.jwasm.Opcodes.PAGE_SIZE;

/**
 * A {@link MemoryConvention} that has a {@link ByteBuffer} as its underlying representation.
 */
public class ByteBufferMemoryConvention extends DelegatingExporter implements MemoryConvention {
    /**
     * An ext that provides access to the underlying byte buffer.
     * <p>
     * For use with optimising {@link #emitMemCopy(IRBuilder, Effect, MemoryConvention)}.
     */
    public static final Ext<ValueGetterSetter> MEMORY_BYTE_BUFFER = Ext.create(ValueGetterSetter.class, "MEMORY_BYTE_BUFFER");

    /**
     * The max number of pages a byte buffer memory can have.
     */
    public static final int MAX_PAGES = Integer.MAX_VALUE / PAGE_SIZE;

    /**
     * An empty {@link JClass} of {@link ByteOrder}.
     */
    public static final JClass BYTE_ORDER_CLASS = JClass.emptyFromJava(ByteOrder.class);

    /**
     * {@link ByteBuffer#slice()}
     */
    public static final JClass.JavaMethod BUFFER_SLICE = IRUtils.BYTE_BUFFER_CLASS.lookupMethod("slice");
    /**
     * {@link Buffer#position(int)}
     */
    public static final JClass.JavaMethod BUFFER_POSITION = IRUtils.BUFFER_CLASS.lookupMethod("position", int.class);
    /**
     * {@link Buffer#limit(int)}
     */
    public static final JClass.JavaMethod BUFFER_LIMIT = IRUtils.BUFFER_CLASS.lookupMethod("limit", int.class);
    /**
     * {@link ByteBuffer#put(ByteBuffer)}
     */
    public static final JClass.JavaMethod BUFFER_PUT_BUF = IRUtils.BYTE_BUFFER_CLASS.lookupMethod("put", ByteBuffer.class);
    /**
     * Get {@link ByteOrder#LITTLE_ENDIAN}
     */
    private static final Op BYTE_ORDER_LE = JavaOps.GET_FIELD
            .create(JClass.JavaField.fromJava(ByteOrder.class, "LITTLE_ENDIAN"));

    private final ValueGetterSetter buffer;
    private final @Nullable Integer max;

    /**
     * Construct a {@link ByteBufferMemoryConvention}.
     *
     * @param exporter The exporter.
     * @param buffer   The getter/setter for the underlying byte buffer.
     * @param max      The maximum of the memory's type.
     */
    public ByteBufferMemoryConvention(
            ExportableConvention exporter,
            ValueGetterSetter buffer,
            @Nullable Integer max
    ) {
        super(exporter);
        this.buffer = buffer;
        attachExt(MEMORY_BYTE_BUFFER, buffer);
        this.max = max;
    }

    @Override
    public void emitMemLoad(IRBuilder ib, Effect effect) {
        WasmOps.WithMemArg<WasmOps.DerefType> wmArg = WasmOps.MEM_LOAD.cast(effect.insn().op).arg;
        WasmOps.DerefType derefType = wmArg.value;

        JClass.JavaMethod toInvoke = new JClass.JavaMethod(
                IRUtils.BYTE_BUFFER_CLASS,
                derefType.load.funcName,
                derefType.load.desc,
                Opcodes.ACC_PUBLIC
        );
        Var ptr = effect.insn().args().get(0);
        Insn loadInsn = JavaOps.INVOKE.create(toInvoke).insn(buffer.get(ib), IRUtils.getAddr(ib, wmArg, ptr));
        if (derefType.ext.insns.size() == 0) {
            ib.insert(loadInsn.copyFrom(effect));
        } else {
            Var loaded = ib.insert(loadInsn, "loaded");
            ib.insert(JavaOps.INSNS
                    .create(Instructions.copyList(derefType.ext.insns))
                    .insn(loaded)
                    .copyFrom(effect));
        }
    }

    @Override
    public void emitMemStore(IRBuilder ib, Effect effect) {
        WasmOps.WithMemArg<WasmOps.StoreType> wmArg = WasmOps.MEM_STORE.cast(effect.insn().op).arg;
        ib.insert(JavaOps.INSNS
                        .create(Instructions.copyList(wmArg.value.insns))
                        .insn(
                                buffer.get(ib),
                                IRUtils.getAddr(ib, wmArg, effect.insn().args().get(0)),
                                effect.insn().args().get(1)
                        ),
                "drop");
    }

    @Override
    public void emitMemSize(IRBuilder ib, Effect effect) {
        ib.insert(JavaOps.IDIV_U
                .insn(ib.insert(JavaOps.INVOKE
                                        .create(IRUtils.BUFFER_CLASS.lookupMethod("capacity"))
                                        .insn(buffer.get(ib)),
                                "rawSz"),
                        ib.insert(CommonOps.constant(PAGE_SIZE), "pgSz"))
                .copyFrom(effect));
    }

    @SuppressWarnings({"CommentedOutCode", "DuplicatedCode"})
    @Override
    public void emitMemGrow(IRBuilder ib, Effect effect) {
        // This is what we are implementing:
        /*
        int sz = theBuf.capacity() / PAGE_SIZE;
        int newSz = sz + growByPages;
        int res;
        if (growByPages < 0 || newSz >= Math.min(Short.MAX_VALUE / 2, max)) {
            res = -1;
        } else {
            try {
                ByteBuffer newBuf = ByteBuffer
                        .allocateDirect(theBuf.capacity() + growByPages * PAGE_SIZE)
                        .order(ByteOrder.LITTLE_ENDIAN);
                newBuf.duplicate().put(theBuf);
                theBuf = newBuf;
                res = sz;
            } catch (OutOfMemoryError e) {
                res = -1;
            }
        }
        return res;
         */

        JClass.JavaMethod capacity = IRUtils.BYTE_BUFFER_CLASS.lookupMethod("capacity");
        JClass.JavaMethod duplicate = IRUtils.BYTE_BUFFER_CLASS.lookupMethod("duplicate");
        Type oome = Type.getType(OutOfMemoryError.class);

        Var growBy = effect.insn().args().get(0);
        BasicBlock failBlock = ib.func.newBb();

        BasicBlock k = ib.func.newBb();
        ib.insertCtrl(JavaOps.BR_COND.create(JavaOps.JumpType.IFLT).insn(growBy).jumpsTo(failBlock, k));
        ib.setBlock(k);

        Var theBuf = buffer.get(ib);
        Var rawSz = ib.insert(JavaOps.INVOKE.create(capacity).insn(theBuf), "rawSz");
        Var sz = ib.insert(JavaOps.insns(new InsnNode(Opcodes.IDIV))
                        .insn(rawSz, ib.insert(CommonOps.constant(PAGE_SIZE), "psz")),
                "sz");
        Var newSz = ib.insert(JavaOps.IADD
                        .insn(sz, growBy),
                "newSz");

        k = ib.func.newBb();
        ib.insertCtrl(JavaOps.BR_COND.create(JavaOps.JumpType.IF_ICMPGT)
                .insn(newSz, ib.insert(CommonOps.constant(max == null ? MAX_PAGES : Math.min(MAX_PAGES, max)), "max"))
                .jumpsTo(failBlock, k));
        ib.setBlock(k);

        k = ib.func.newBb();
        BasicBlock catchBlock = ib.func.newBb();
        ib.insertCtrl(JavaOps.TRY.create(oome).insn().jumpsTo(catchBlock, k));
        ib.setBlock(k);

        Var newBuf = ib.insert(JavaOps.INVOKE
                        .create(IRUtils.BYTE_BUFFER_CLASS.lookupMethod("order", ByteOrder.class))
                        .insn(ib.insert(JavaOps.INVOKE
                                                .create(IRUtils.BYTE_BUFFER_CLASS.lookupMethod("allocateDirect", int.class))
                                                .insn(ib.insert(JavaOps.IADD
                                                                .insn(rawSz,
                                                                        ib.insert(JavaOps.IMUL
                                                                                        .insn(ib.insert(CommonOps.constant(PAGE_SIZE), "psz"),
                                                                                                growBy),
                                                                                "byRaw")),
                                                        "newSzRaw")),
                                        "newBuf"),
                                ib.insert(BYTE_ORDER_LE.insn(), "order")),
                "newBufLE");
        ib.insert(JavaOps.INVOKE
                        .create(BUFFER_PUT_BUF)
                        .insn(ib.insert(JavaOps.INVOKE.create(duplicate).insn(newBuf), "dupTgt"),
                                ib.insert(JavaOps.INVOKE.create(duplicate).insn(theBuf), "dupSrc")),
                "put");
        buffer.set(ib, newBuf);
        BasicBlock end = ib.func.newBb();
        ib.insertCtrl(Control.br(end));
        BasicBlock successBlock = ib.getBlock();

        ib.setBlock(catchBlock);
        ib.insert(JavaOps.CATCH.create(oome).insn(), "oome");
        ib.insertCtrl(Control.br(failBlock));

        ib.setBlock(failBlock);
        Var err = ib.insert(CommonOps.constant(-1), "err");
        ib.insertCtrl(Control.br(end));

        ib.setBlock(end);
        ib.insert(CommonOps.PHI.create(Arrays.asList(successBlock, failBlock))
                .insn(sz, err)
                .copyFrom(effect));
    }

    private static Var position(IRBuilder ib, Var buffer, Var to) {
        if (CommonOps.quickCheckConstant(to, 0)) return buffer;
        return ib.insert(JavaOps.INVOKE.create(BUFFER_POSITION).insn(buffer, to), "positioned");
    }

    @Override
    public void emitMemInit(IRBuilder ib, Effect effect, Var data) {
        Iterator<Var> iter = effect.insn().args().iterator();
        Var dstIdx = iter.next();
        Var srcIdx = iter.next();
        Var length = iter.next();

        Var mem = buffer.get(ib);

        mem = ib.insert(JavaOps.INVOKE.create(BUFFER_SLICE).insn(mem), "sliced");
        position(ib, mem, dstIdx);

        data = ib.insert(JavaOps.INVOKE.create(BUFFER_SLICE).insn(data), "sliced");
        Var toLimit = position(ib, data, srcIdx);
        Var limit = ib.insert(JavaOps.IADD.insn(srcIdx, length), "limit");
        ib.insert(JavaOps.INVOKE.create(BUFFER_LIMIT).insn(toLimit, limit), "_limited");

        ib.insert(JavaOps.INVOKE.create(BUFFER_PUT_BUF).insn(mem, data), "mem");
    }

    @Override
    public void emitBoundsCheck(IRBuilder ib, int mem, Var bound) {
        Var szRaw = ib.insert(JavaOps.INVOKE
                        .create(IRUtils.BUFFER_CLASS.lookupMethod("capacity"))
                        .insn(buffer.get(ib)),
                "rawSz");
        szRaw = ib.insert(JavaOps.I2L.insn(szRaw), "rawSzL");
        IRUtils.trapWhen(ib, JavaOps.BR_COND.create(JavaOps.JumpType.IFGT)
                        .insn(ib.insert(JavaOps.insns(new InsnNode(Opcodes.LCMP))
                                        .insn(bound, szRaw),
                                "cmp")),
                "out of bounds memory access");
    }

    @SuppressWarnings({"DuplicatedCode", "CommentedOutCode"})
    @Override
    public void emitMemCopy(IRBuilder ib, Effect effect, MemoryConvention dst) {
        Optional<ValueGetterSetter> bbuf = dst.getExt(MEMORY_BYTE_BUFFER);
        if (bbuf.isPresent()) {
            ValueGetterSetter otherBuf = bbuf.get();

            Pair<Integer, Integer> arg = WasmOps.MEM_COPY.cast(effect.insn().op).arg;
            int thisIdx = arg.left;
            int otherIdx = arg.right;
            Iterator<Var> iter = effect.insn().args().iterator();
            Var dstAddr = iter.next();
            Var srcAddr = iter.next();
            Var len = iter.next();

            Var lenLong = ib.insert(JavaOps.I2L_U.insn(len), "lenL");
            emitBoundsCheck(ib, thisIdx, ib.insert(JavaOps.LADD.insn(ib.insert(JavaOps.I2L_U.insn(srcAddr), "sL"), lenLong),
                    "srcEnd"));
            emitBoundsCheck(ib, otherIdx, ib.insert(JavaOps.LADD.insn(ib.insert(JavaOps.I2L_U.insn(dstAddr), "dL"), lenLong),
                    "dstEnd"));

            // ByteBuffer o = ByteBuffer.allocate(0);
            // ByteBuffer t = ByteBuffer.allocate(0);
            // int srcA = 0, dstA = 0, lenI = 0;
            // t.slice().position(dstA).put(o.slice().position(srcA).limit(lenI));
            Var target = otherBuf.get(ib);
            target = ib.insert(JavaOps.INVOKE.create(BUFFER_SLICE).insn(target), "sliced");
            position(ib, target, dstAddr);
            Var src = buffer.get(ib);
            src = ib.insert(JavaOps.INVOKE.create(BUFFER_SLICE).insn(src), "sliced");
            Var positioned = position(ib, src, srcAddr);
            Var limit = ib.insert(JavaOps.IADD.insn(srcAddr, len), "limit");
            ib.insert(JavaOps.INVOKE.create(BUFFER_LIMIT).insn(positioned, limit), "limited");
            ib.insert(JavaOps.INVOKE.create(BUFFER_PUT_BUF).insn(target, src), "put");
        } else {
            MemoryConvention.super.emitMemCopy(ib, effect, dst);
        }
    }
}

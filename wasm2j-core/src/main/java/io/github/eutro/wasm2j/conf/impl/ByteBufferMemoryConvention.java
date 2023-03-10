package io.github.eutro.wasm2j.conf.impl;

import io.github.eutro.wasm2j.conf.api.ExportableConvention;
import io.github.eutro.wasm2j.conf.api.MemoryConvention;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.util.IRUtils;
import io.github.eutro.wasm2j.util.Instructions;
import io.github.eutro.wasm2j.util.Pair;
import io.github.eutro.wasm2j.util.ValueGetterSetter;
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

public class ByteBufferMemoryConvention extends DelegatingExporter implements MemoryConvention {
    public static final Ext<ValueGetterSetter> MEMORY_BYTE_BUFFER = Ext.create(ValueGetterSetter.class, "MEMORY_BYTE_BUFFER");
    public static final int MAX_PAGES = Integer.MAX_VALUE / PAGE_SIZE;
    private static final JavaExts.JavaMethod BUFFER_SLICE = JavaExts.JavaMethod.fromJava(ByteBuffer.class, "slice");
    private static final JavaExts.JavaMethod BUFFER_POSITION = JavaExts.JavaMethod.fromJava(ByteBuffer.class, "position", int.class);
    private static final JavaExts.JavaMethod BUFFER_LIMIT = JavaExts.JavaMethod.fromJava(ByteBuffer.class, "limit", int.class);
    private static final JavaExts.JavaMethod BUFFER_PUT_BUF = JavaExts.JavaMethod.fromJava(ByteBuffer.class, "put", ByteBuffer.class);

    private final ValueGetterSetter buffer;
    private final @Nullable Integer max;

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

        JavaExts.JavaMethod toInvoke = new JavaExts.JavaMethod(
                IRUtils.BYTE_BUFFER_CLASS,
                derefType.load.funcName,
                derefType.load.desc,
                JavaExts.JavaMethod.Kind.VIRTUAL
        );
        Var ptr = effect.insn().args.get(0);
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
                                IRUtils.getAddr(ib, wmArg, effect.insn().args.get(0)),
                                effect.insn().args.get(1)
                        ),
                "drop");
    }

    @Override
    public void emitMemSize(IRBuilder ib, Effect effect) {
        ib.insert(JavaOps.INVOKE
                .create(new JavaExts.JavaMethod(
                        new JavaExts.JavaClass(Type.getInternalName(Integer.class)),
                        "divideUnsigned",
                        "(II)I",
                        JavaExts.JavaMethod.Kind.STATIC
                ))
                .insn(ib.insert(JavaOps.INVOKE
                                        .create(new JavaExts.JavaMethod(
                                                new JavaExts.JavaClass(Type.getInternalName(Buffer.class)),
                                                "capacity",
                                                "()I",
                                                JavaExts.JavaMethod.Kind.VIRTUAL
                                        ))
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

        JavaExts.JavaMethod capacity = JavaExts.JavaMethod.fromJava(ByteBuffer.class, "capacity");
        JavaExts.JavaMethod duplicate = JavaExts.JavaMethod.fromJava(ByteBuffer.class, "duplicate");
        Type oome = Type.getType(OutOfMemoryError.class);

        Var growBy = effect.insn().args.get(0);
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
                        .create(JavaExts.JavaMethod.fromJava(ByteBuffer.class, "order", ByteOrder.class))
                        .insn(ib.insert(JavaOps.INVOKE
                                                .create(JavaExts.JavaMethod.fromJava(ByteBuffer.class, "allocateDirect", int.class))
                                                .insn(ib.insert(JavaOps.IADD
                                                                .insn(rawSz,
                                                                        ib.insert(JavaOps.IMUL
                                                                                        .insn(ib.insert(CommonOps.constant(PAGE_SIZE), "psz"),
                                                                                                growBy),
                                                                                "byRaw")),
                                                        "newSzRaw")),
                                        "newBuf"),
                                ib.insert(JavaOps.GET_FIELD
                                                .create(JavaExts.JavaField.fromJava(ByteOrder.class, "LITTLE_ENDIAN"))
                                                .insn(),
                                        "order")),
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

    @Override
    public void emitMemInit(IRBuilder ib, Effect effect, Var data) {
        Iterator<Var> iter = effect.insn().args.iterator();
        Var dstIdx = iter.next();
        Var srcIdx = iter.next();
        Var length = iter.next();

        Var mem = buffer.get(ib);
        JavaExts.JavaMethod sliceMethod = new JavaExts.JavaMethod(
                IRUtils.BYTE_BUFFER_CLASS,
                "slice",
                "()Ljava/nio/ByteBuffer;",
                JavaExts.JavaMethod.Kind.VIRTUAL
        );
        JavaExts.JavaMethod positionMethod = new JavaExts.JavaMethod(
                IRUtils.BUFFER_CLASS,
                "position",
                "(I)Ljava/nio/Buffer;",
                JavaExts.JavaMethod.Kind.VIRTUAL
        );
        JavaExts.JavaMethod limitMethod = new JavaExts.JavaMethod(
                IRUtils.BUFFER_CLASS,
                "limit",
                "(I)Ljava/nio/Buffer;",
                JavaExts.JavaMethod.Kind.VIRTUAL
        );
        JavaExts.JavaMethod putMethod = new JavaExts.JavaMethod(
                IRUtils.BYTE_BUFFER_CLASS,
                "put",
                "(Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;",
                JavaExts.JavaMethod.Kind.VIRTUAL
        );

        mem = ib.insert(JavaOps.INVOKE.create(sliceMethod).insn(mem), "sliced");
        ib.insert(JavaOps.INVOKE.create(positionMethod).insn(mem, dstIdx), "_positioned");

        data = ib.insert(JavaOps.INVOKE.create(sliceMethod).insn(data), "sliced");
        ib.insert(JavaOps.INVOKE.create(positionMethod).insn(data, srcIdx), "_positioned");
        Var limit = ib.insert(JavaOps.IADD.insn(srcIdx, length), "limit");
        ib.insert(JavaOps.INVOKE.create(limitMethod).insn(data, limit), "_limited");

        ib.insert(JavaOps.INVOKE.create(putMethod).insn(mem, data), "mem");
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
            Iterator<Var> iter = effect.insn().args.iterator();
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
            target = ib.insert(JavaOps.INVOKE.create(BUFFER_POSITION).insn(target, dstAddr), "positioned");
            Var src = buffer.get(ib);
            src = ib.insert(JavaOps.INVOKE.create(BUFFER_SLICE).insn(src), "sliced");
            src = ib.insert(JavaOps.INVOKE.create(BUFFER_POSITION).insn(src, srcAddr), "positioned");
            Var limit = ib.insert(JavaOps.IADD.insn(srcAddr, len), "limit");
            src = ib.insert(JavaOps.INVOKE.create(BUFFER_LIMIT).insn(src, limit), "limited");
            ib.insert(JavaOps.INVOKE.create(BUFFER_PUT_BUF).insn(target, src), "put");
        } else {
            MemoryConvention.super.emitMemCopy(ib, effect, dst);
        }
    }
}

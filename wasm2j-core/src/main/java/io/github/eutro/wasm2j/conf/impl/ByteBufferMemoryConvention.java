package io.github.eutro.wasm2j.conf.impl;

import io.github.eutro.wasm2j.conf.api.ExportableConvention;
import io.github.eutro.wasm2j.conf.api.MemoryConvention;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.util.IRUtils;
import io.github.eutro.wasm2j.util.Instructions;
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

import static io.github.eutro.jwasm.Opcodes.PAGE_SIZE;

public class ByteBufferMemoryConvention extends DelegatingExporter implements MemoryConvention {
    public static final int MAX_PAGES = Integer.MAX_VALUE / PAGE_SIZE;
    private final ValueGetterSetter buffer;
    private final @Nullable Integer max;

    public ByteBufferMemoryConvention(
            ExportableConvention exporter,
            ValueGetterSetter buffer,
            @Nullable Integer max
    ) {
        super(exporter);
        this.buffer = buffer;
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
                        .create(JavaExts.JavaMethod.fromJava(ByteBuffer.class, "put", ByteBuffer.class))
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
                IRUtils.BYTE_BUFFER_CLASS,
                "position",
                "(I)Ljava/nio/ByteBuffer;",
                JavaExts.JavaMethod.Kind.VIRTUAL
        );
        JavaExts.JavaMethod limitMethod = new JavaExts.JavaMethod(
                IRUtils.BYTE_BUFFER_CLASS,
                "limit",
                "(I)Ljava/nio/ByteBuffer;",
                JavaExts.JavaMethod.Kind.VIRTUAL
        );
        JavaExts.JavaMethod putMethod = new JavaExts.JavaMethod(
                IRUtils.BYTE_BUFFER_CLASS,
                "put",
                "(Ljava/nio/ByteBuffer;)Ljava/nio/ByteBuffer;",
                JavaExts.JavaMethod.Kind.VIRTUAL
        );

        mem = ib.insert(JavaOps.INVOKE.create(sliceMethod).insn(mem), "sliced");
        mem = ib.insert(JavaOps.INVOKE.create(positionMethod).insn(mem, dstIdx), "positioned");

        data = ib.insert(JavaOps.INVOKE.create(sliceMethod).insn(data), "sliced");
        data = ib.insert(JavaOps.INVOKE.create(positionMethod).insn(data, srcIdx), "positioned");
        Var limit = ib.insert(JavaOps.IADD.insn(srcIdx, length), "limit");
        data = ib.insert(JavaOps.INVOKE.create(limitMethod).insn(data, limit), "limited");

        ib.insert(JavaOps.INVOKE.create(putMethod).insn(mem, data), "mem");
    }
}
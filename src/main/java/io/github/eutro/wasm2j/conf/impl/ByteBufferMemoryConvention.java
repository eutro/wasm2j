package io.github.eutro.wasm2j.conf.impl;

import io.github.eutro.wasm2j.conf.api.ExportableConvention;
import io.github.eutro.wasm2j.conf.api.MemoryConvention;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.Insn;
import io.github.eutro.wasm2j.ssa.Var;
import io.github.eutro.wasm2j.util.IRUtils;
import io.github.eutro.wasm2j.util.Instructions;
import io.github.eutro.wasm2j.util.ValueGetter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;

import java.nio.Buffer;

import static io.github.eutro.wasm2j.ext.CommonExts.markPure;

public class ByteBufferMemoryConvention extends DelegatingExporter implements MemoryConvention {
    private final ValueGetter buffer;

    public ByteBufferMemoryConvention(ExportableConvention exporter, ValueGetter buffer) {
        super(exporter);
        this.buffer = buffer;
    }

    @Override
    public void emitMemLoad(IRBuilder ib, Effect effect) {
        WasmOps.WithMemArg<WasmOps.DerefType> wmArg = WasmOps.MEM_LOAD.cast(effect.insn().op).arg;
        WasmOps.DerefType derefType = wmArg.value;

        JavaExts.JavaMethod toInvoke = new JavaExts.JavaMethod(
                IRUtils.BYTE_BUFFER_CLASS,
                derefType.load.funcName,
                derefType.load.desc,
                JavaExts.JavaMethod.Type.VIRTUAL
        );
        Var ptr = effect.insn().args.get(0);
        Insn loadInsn = JavaOps.INVOKE.create(toInvoke).insn(getMem(ib), getAddr(ib, wmArg, ptr));
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
        InsnList insns = Instructions.copyList(wmArg.value.insns);
        insns.add(new InsnNode(Opcodes.POP));
        ib.insert(JavaOps.INSNS
                .create(insns)
                .insn(
                        getMem(ib),
                        getAddr(ib, wmArg, effect.insn().args.get(1)),
                        effect.insn().args.get(0)
                )
                .assignTo());
    }

    @Override
    public void emitMemSize(IRBuilder ib, Effect effect) {
        ib.insert(JavaOps.INVOKE
                .create(new JavaExts.JavaMethod(
                        new JavaExts.JavaClass(Type.getInternalName(Buffer.class)),
                        "capacity",
                        "()I",
                        JavaExts.JavaMethod.Type.VIRTUAL
                ))
                .insn(getMem(ib))
                .copyFrom(effect));
    }

    @Override
    public void emitMemGrow(IRBuilder ib, Effect effect) {
        ib.insert(CommonOps.CONST.create(0).insn().copyFrom(effect));
    }

    private Var getAddr(IRBuilder ib, WasmOps.WithMemArg<?> wmArg, Var ptr) {
        return wmArg.offset == 0
                ? ptr
                : ib.insert(markPure(JavaOps.insns(new InsnNode(Opcodes.IADD)))
                        .insn(ptr,
                                ib.insert(CommonOps.CONST.create(wmArg.offset).insn(),
                                        "offset")),
                "addr");
    }

    private Var getMem(IRBuilder ib) {
        return buffer.get(ib);
    }
}

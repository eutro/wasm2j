package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.util.ValueGetterSetter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.TypeInsnNode;

public interface ElemConvention {
    Type elementType();

    ValueGetterSetter array();

    default void emitDrop(IRBuilder ib, Effect fx) {
        ValueGetterSetter array = array();
        array.set(ib, ib
                .insert(JavaOps.insns(new TypeInsnNode(Opcodes.ANEWARRAY, elementType().getInternalName()))
                        .insn(ib.insert(CommonOps.constant(0), "len")),
                        "arr"));
    }
}

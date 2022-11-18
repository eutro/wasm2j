package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ssa.Effect;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.util.ValueGetterSetter;

import java.nio.ByteBuffer;

public interface DataConvention {
    ValueGetterSetter byteBuffer();

    default void emitDrop(IRBuilder ib, Effect fx) {
        byteBuffer().set(ib,
                ib.insert(JavaOps.INVOKE
                        .create(JavaExts.JavaMethod.fromJava(ByteBuffer.class, "allocate", int.class))
                        .insn(ib.insert(CommonOps.constant(0), "sz")),
                        "empty"));
    }
}

package io.github.eutro.wasm2j.core.conf.itf;

import io.github.eutro.wasm2j.core.ops.CommonOps;
import io.github.eutro.wasm2j.core.ops.JavaOps;
import io.github.eutro.wasm2j.core.ssa.Effect;
import io.github.eutro.wasm2j.core.ssa.IRBuilder;
import io.github.eutro.wasm2j.core.util.IRUtils;
import io.github.eutro.wasm2j.core.ops.WasmOps;
import io.github.eutro.wasm2j.core.util.ValueGetterSetter;

import java.nio.ByteBuffer;

/**
 * A convention that defines how to retrieve the contents of a data.
 *
 * @see WirJavaConvention
 */
public interface DataConvention {
    /**
     * Get the {@link ValueGetterSetter} that accesses the element segment,
     * as a {@link ByteBuffer}.
     *
     * @return A {@link ValueGetterSetter} to the array.
     */
    ValueGetterSetter byteBuffer();

    /**
     * Emit code to drop the data.
     * <p>
     * By default, this stores a new 0-length {@link ByteBuffer} to {@link #byteBuffer()}.
     *
     * @param ib The instruction builder.
     * @param fx The {@link WasmOps#DATA_DROP} instruction.
     */
    default void emitDrop(IRBuilder ib, Effect fx) {
        byteBuffer().set(ib,
                ib.insert(JavaOps.INVOKE
                                .create(IRUtils.BYTE_BUFFER_CLASS.lookupMethod("allocate", int.class))
                                .insn(ib.insert(CommonOps.constant(0), "sz")),
                        "empty"));
    }
}

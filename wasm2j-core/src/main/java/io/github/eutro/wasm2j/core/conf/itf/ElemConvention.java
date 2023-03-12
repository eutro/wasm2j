package io.github.eutro.wasm2j.core.conf.itf;

import io.github.eutro.wasm2j.core.ops.CommonOps;
import io.github.eutro.wasm2j.core.ops.JavaOps;
import io.github.eutro.wasm2j.core.ssa.Effect;
import io.github.eutro.wasm2j.core.ssa.IRBuilder;
import io.github.eutro.wasm2j.core.ops.WasmOps;
import io.github.eutro.wasm2j.core.util.ValueGetterSetter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.TypeInsnNode;

/**
 * A convention that defines how to retrieve the contents and type of an element.
 *
 * @see WirJavaConvention
 */
public interface ElemConvention {
    /**
     * Retrieve the component type of the element.
     *
     * @return The component type of the element.
     */
    Type elementType();

    /**
     * Get the {@link ValueGetterSetter} that accesses the element,
     * as a Java array.
     *
     * @return A {@link ValueGetterSetter} to the array.
     */
    ValueGetterSetter array();

    /**
     * Emit code to drop the element.
     * <p>
     * By default, this stores a new 0-length array to {@link #array()}.
     *
     * @param ib The instruction builder.
     * @param fx The {@link WasmOps#ELEM_DROP} instruction.
     */
    default void emitDrop(IRBuilder ib, Effect fx) {
        ValueGetterSetter array = array();
        array.set(ib, ib
                .insert(JavaOps.insns(new TypeInsnNode(Opcodes.ANEWARRAY, elementType().getInternalName()))
                                .insn(ib.insert(CommonOps.constant(0), "len")),
                        "arr"));
    }
}

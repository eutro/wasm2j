package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.tree.AbstractInsnNode;
import io.github.eutro.jwasm.tree.PrefixInsnNode;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class Opcode {
    public final byte byteOpcode;
    @Nullable
    public final Integer intOpcode;

    public Opcode(byte byteOpcode, @Nullable Integer intOpcode) {
        this.byteOpcode = byteOpcode;
        this.intOpcode = intOpcode;
    }

    public Opcode(AbstractInsnNode insn) {
        this(insn.opcode,
                insn instanceof PrefixInsnNode
                        ? ((PrefixInsnNode) insn).intOpcode
                        : null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Opcode opcode = (Opcode) o;
        return byteOpcode == opcode.byteOpcode && Objects.equals(intOpcode, opcode.intOpcode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(byteOpcode, intOpcode);
    }
}

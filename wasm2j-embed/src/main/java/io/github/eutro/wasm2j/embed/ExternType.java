package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.Opcodes;

public enum ExternType {
    FUNC,
    TABLE,
    MEM,
    GLOBAL,
    ;

    public static ExternType fromByte(byte importType) {
        switch (importType) {
            case Opcodes.IMPORTS_FUNC:
                return FUNC;
            case Opcodes.IMPORTS_TABLE:
                return TABLE;
            case Opcodes.IMPORTS_MEM:
                return MEM;
            case Opcodes.IMPORTS_GLOBAL:
                return GLOBAL;
            default:
                throw new IllegalArgumentException();
        }
    }
}

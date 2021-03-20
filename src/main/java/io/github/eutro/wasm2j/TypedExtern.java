package io.github.eutro.wasm2j;

import org.objectweb.asm.tree.FieldNode;

interface TypedExtern extends Extern {
    byte type();

    class ModuleTypedExtern extends Extern.ModuleFieldExtern implements TypedExtern {
        private final byte type;

        public ModuleTypedExtern(FieldNode fn, String internalName, byte type) {
            super(fn, internalName);
            this.type = type;
        }

        @Override
        public byte type() {
            return type;
        }
    }
}

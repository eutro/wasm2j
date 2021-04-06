package io.github.eutro.wasm2j;

import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.FieldNode;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.PUTFIELD;

public interface Extern {
    void emitGet(GeneratorAdapter mv);

    default boolean emitSet(GeneratorAdapter mv) {
        return false;
    }

    class ModuleFieldExtern implements Extern {
        private final FieldNode fn;
        private final String internalName;

        public ModuleFieldExtern(FieldNode fn, String internalName) {
            this.fn = fn;
            this.internalName = internalName;
        }

        @Override
        public void emitGet(GeneratorAdapter mv) {
            mv.loadThis();
            mv.visitFieldInsn(GETFIELD, internalName, fn.name, fn.desc);
        }

        @Override
        public boolean emitSet(GeneratorAdapter mv) {
            mv.loadThis();
            mv.swap();
            mv.visitFieldInsn(PUTFIELD, internalName, fn.name, fn.desc);
            return true;
        }
    }
}

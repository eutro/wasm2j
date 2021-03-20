package io.github.eutro.wasm2j;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.FieldNode;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Opcodes.PUTFIELD;

interface Extern {
    void emitGet(MethodVisitor mv);

    default boolean emitSet(MethodVisitor mv) {
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
        public void emitGet(MethodVisitor mv) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitFieldInsn(GETFIELD, internalName, fn.name, fn.desc);
        }

        @Override
        public boolean emitSet(MethodVisitor mv) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitInsn(SWAP);
            mv.visitFieldInsn(PUTFIELD, internalName, fn.name, fn.desc);
            return true;
        }
    }
}

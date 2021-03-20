package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.tree.TypeNode;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import static org.objectweb.asm.Opcodes.*;

interface FuncExtern extends Extern {
    void emitInvoke(Context mv);

    class ModuleFuncExtern implements FuncExtern {
        private final ClassNode cn;
        private final MethodNode mn;
        private final TypeNode type;

        public ModuleFuncExtern(ClassNode cn, MethodNode mn, TypeNode type) {
            this.cn = cn;
            this.mn = mn;
            this.type = type;
        }

        @Override
        public void emitGet(MethodVisitor mv) {
            // MethodHandles.insertArguments(<handle>, 0, new Object[] { this })
            mv.visitLdcInsn(new Handle(H_INVOKEVIRTUAL, cn.name, mn.name, mn.desc, false));
            mv.visitInsn(ICONST_0);
            mv.visitInsn(ICONST_1);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_0);
            mv.visitVarInsn(ALOAD, 0);
            mv.visitInsn(AASTORE);
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "insertArguments",
                    "(Ljava/lang/invoke/MethodHandle;I[Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;",
                    false);
        }

        @Override
        public void emitInvoke(Context mv) {
            int[] locals = new int[type.params.length];
            for (int i = type.params.length - 1; i >= 0; i--) {
                mv.storeLocal(locals[i] = mv.newLocal(Types.toJava(type.params[i])));
            }
            mv.visitVarInsn(ALOAD, 0);
            for (int local : locals) {
                mv.loadLocal(local);
            }
            mv.visitMethodInsn(INVOKEVIRTUAL, cn.name, mn.name, mn.desc, false);
        }

    }
}

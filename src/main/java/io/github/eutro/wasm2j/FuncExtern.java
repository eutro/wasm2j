package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.tree.TypeNode;
import org.objectweb.asm.Handle;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import static org.objectweb.asm.Opcodes.*;

public interface FuncExtern extends Extern {
    void emitInvoke(GeneratorAdapter mv);
    TypeNode type();

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
        public void emitGet(GeneratorAdapter mv) {
            // MethodHandles.insertArguments(<handle>, 0, new Object[] { this })
            mv.visitLdcInsn(new Handle(H_INVOKEVIRTUAL, cn.name, mn.name, mn.desc, false));
            mv.push(0);
            mv.push(1);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            mv.dup();
            mv.push(0);
            mv.loadThis();
            mv.visitInsn(AASTORE);
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "insertArguments",
                    "(Ljava/lang/invoke/MethodHandle;I[Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;",
                    false);
        }

        @Override
        public void emitInvoke(GeneratorAdapter mv) {
            int[] locals = new int[type.params.length];
            for (int i = type.params.length - 1; i >= 0; i--) {
                mv.storeLocal(locals[i] = mv.newLocal(Types.toJava(type.params[i])));
            }
            mv.loadThis();
            for (int local : locals) {
                mv.loadLocal(local);
            }
            mv.visitMethodInsn(INVOKEVIRTUAL, cn.name, mn.name, mn.desc, false);
        }

        @Override
        public TypeNode type() {
            return type;
        }

    }
}

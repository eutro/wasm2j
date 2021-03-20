package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.tree.*;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static io.github.eutro.jwasm.Opcodes.MUT_CONST;
import static org.objectweb.asm.Opcodes.*;

public class InteropModuleAdapter extends ModuleAdapter {

    public static final String PREFIX = "java:";

    public InteropModuleAdapter(@NotNull ModuleNode node) {
        super(node);
    }

    public InteropModuleAdapter() {
    }

    @Override
    protected @NotNull FuncExtern resolveFuncImport(FuncImportNode imprt) {
        if (!imprt.module.startsWith(PREFIX)) {
            return super.resolveFuncImport(imprt);
        }
        String className = imprt.module.substring(PREFIX.length());
        String internalName = className.replace('.', '/');
        String methodName = imprt.name;
        Type funcType = Types.methodDesc(getType(imprt.type));
        return new FuncExtern() {
            @Override
            public void emitInvoke(Context mv) {
                mv.visitMethodInsn(INVOKESTATIC, internalName, methodName, funcType.toString(), false);
            }

            @Override
            public void emitGet(MethodVisitor mv) {
                mv.visitLdcInsn(new Handle(H_INVOKESTATIC, internalName, methodName, funcType.toString(), false));
            }
        };
    }

    @Override
    protected @NotNull TypedExtern resolveTableImport(TableImportNode imprt) {
        if (!imprt.module.startsWith(PREFIX)) {
            return super.resolveTableImport(imprt);
        }
        String className = imprt.module.substring(PREFIX.length());
        String internalName = className.replace('.', '/');
        String fieldName = imprt.name;
        byte type = imprt.type;
        return new TypedExtern() {
            @Override
            public byte type() {
                return type;
            }

            @Override
            public void emitGet(MethodVisitor mv) {
                mv.visitFieldInsn(GETSTATIC, internalName, fieldName, "[" + Types.toJava(type()));
            }

            @Override
            public boolean emitSet(MethodVisitor mv) {
                mv.visitFieldInsn(PUTSTATIC, internalName, fieldName, "[" + Types.toJava(type()));
                return true;
            }
        };
    }

    @Override
    protected @NotNull Extern resolveMemImport(MemImportNode imprt) {
        if (!imprt.module.startsWith(PREFIX)) {
            return super.resolveMemImport(imprt);
        }
        String className = imprt.module.substring(PREFIX.length());
        String internalName = className.replace('.', '/');
        String fieldName = imprt.name;
        return mv -> mv.visitFieldInsn(GETSTATIC, internalName, fieldName, "Ljava/nio/ByteBuffer;");
    }

    @Override
    protected @NotNull TypedExtern resolveGlobalImport(GlobalImportNode imprt) {
        if (!imprt.module.startsWith(PREFIX)) {
            return super.resolveGlobalImport(imprt);
        }
        String className = imprt.module.substring(PREFIX.length());
        String internalName = className.replace('.', '/');
        String fieldName = imprt.name;
        GlobalTypeNode type = imprt.type;
        return new TypedExtern() {
            @Override
            public byte type() {
                return type.type;
            }

            @Override
            public void emitGet(MethodVisitor mv) {
                mv.visitFieldInsn(GETSTATIC, internalName, fieldName, Types.toJava(type()).toString());
            }

            @Override
            public boolean emitSet(MethodVisitor mv) {
                if (type.mut == MUT_CONST) return false;
                mv.visitFieldInsn(PUTSTATIC, internalName, fieldName, Types.toJava(type()).toString());
                return true;
            }
        };
    }
}

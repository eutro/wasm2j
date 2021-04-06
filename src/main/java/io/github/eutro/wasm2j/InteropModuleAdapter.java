package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.tree.FuncImportNode;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.jwasm.tree.TypeNode;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.eutro.jwasm.Opcodes.I32;
import static org.objectweb.asm.Opcodes.*;

public class InteropModuleAdapter extends ModuleAdapter {

    public static final String MODULE_NAME = "java";

    public static final Pattern UNQUALIFIED_NAME_PATTERN = Pattern.compile("(?:[^.;\\[/]+)");
    public static final Pattern METHOD_NAME_PATTERN = Pattern.compile("(?:[^.;\\[/<>()]+)");
    public static final Pattern INTERNAL_NAME_PATTERN = Pattern.compile("(?:(?:" + UNQUALIFIED_NAME_PATTERN + "/)*" +
                                                                        UNQUALIFIED_NAME_PATTERN + ")");
    public static final Pattern DESC_PATTERN = Pattern.compile("(?:(?:\\[*)" +
                                                               "(?:(?:[BCDFIJSZ])|" +
                                                               "L" + INTERNAL_NAME_PATTERN + ";))");
    public static final Pattern METHOD_DESC_PATTERN = Pattern.compile("(?:\\(" + DESC_PATTERN + "*\\)(?:" + DESC_PATTERN + "|V))");
    public static final Pattern FUNC_PATTERN = Pattern.compile("(?<package>(?:" + UNQUALIFIED_NAME_PATTERN + "\\.)*)" +
                                                               "(?<classname>" + UNQUALIFIED_NAME_PATTERN + ")" +
                                                               "(?<accessor>\\.{1,3}|/)" +
                                                               "(?<method>" + METHOD_NAME_PATTERN + ")" +
                                                               "(?<desc>" + METHOD_DESC_PATTERN + ")?");

    public static final String ALLOC_NAME = "__alloc";
    public static final String ALLOC_DESC = "(Ljava/lang/Object;)I";

    public InteropModuleAdapter(@NotNull ModuleNode node) {
        super(node);
    }

    public InteropModuleAdapter() {
    }

    @Override
    protected @NotNull FuncExtern resolveFuncImport(ClassNode cn, FuncImportNode imprt) {
        Matcher matcher;
        if (!MODULE_NAME.equals(imprt.module)) {
            return super.resolveFuncImport(cn, imprt);
        }
        TypeNode typeNode = getType(imprt.type);
        if ("free".equals(imprt.name)) {
            if (!Arrays.equals(typeNode.params, new byte[] { I32 }) ||
                !Arrays.equals(typeNode.returns, new byte[] {})) {
                return super.resolveFuncImport(cn, imprt);
            }
            FieldNode rn = new FieldNode(ACC_PRIVATE,
                    "__references",
                    "Ljava/util/Map;",
                    "Ljava/util/Map<Ljava/lang/Integer;Ljava/lang/Object;>;",
                    null);
            cn.fields.add(rn);
            {
                FieldNode ct = new FieldNode(ACC_PRIVATE,
                        "__count",
                        "I",
                        null,
                        null);
                cn.fields.add(ct);
                MethodNode mn = new MethodNode();
                mn.name = ALLOC_NAME;
                mn.access = ACC_PRIVATE;
                mn.desc = ALLOC_DESC;

                Label endCreate = new Label();
                mn.visitVarInsn(ALOAD, 0);
                mn.visitInsn(DUP);
                mn.visitInsn(DUP);
                mn.visitFieldInsn(GETFIELD, cn.name, ct.name, ct.desc);
                mn.visitInsn(ICONST_1);
                mn.visitInsn(IADD);
                mn.visitInsn(DUP_X1);
                mn.visitFieldInsn(PUTFIELD, cn.name, ct.name, ct.desc);
                mn.visitInsn(DUP_X1);
                mn.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                mn.visitInsn(SWAP);
                mn.visitFieldInsn(GETFIELD, cn.name, rn.name, rn.desc);
                mn.visitInsn(DUP);
                mn.visitJumpInsn(IFNONNULL, endCreate);

                mn.visitInsn(POP);
                mn.visitVarInsn(ALOAD, 0);
                mn.visitTypeInsn(NEW, "java/util/HashMap");
                mn.visitInsn(DUP);
                mn.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
                mn.visitInsn(DUP_X1);
                mn.visitFieldInsn(PUTFIELD, cn.name, rn.name, rn.desc);

                mn.visitLabel(endCreate);
                mn.visitInsn(SWAP);
                mn.visitVarInsn(ALOAD, 1);
                mn.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
                mn.visitInsn(POP);
                mn.visitInsn(IRETURN);
                cn.methods.add(mn);
            }
            {
                MethodNode mn = new MethodNode();
                mn.name = "__deref";
                mn.access = ACC_PRIVATE;
                mn.desc = "(I)Ljava/lang/Object;";

                Label popThrowExn = new Label();
                Label retNull = new Label();
                mn.visitVarInsn(ALOAD, 0);
                mn.visitFieldInsn(GETFIELD, cn.name, rn.name, rn.desc);
                mn.visitInsn(DUP);
                mn.visitJumpInsn(IFNULL, popThrowExn);

                mn.visitVarInsn(ILOAD, 1);
                mn.visitInsn(DUP);
                mn.visitJumpInsn(IFEQ, retNull);
                mn.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                mn.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
                mn.visitInsn(DUP);
                mn.visitJumpInsn(IFNULL, popThrowExn);
                mn.visitInsn(ARETURN);

                mn.visitLabel(retNull);
                mn.visitInsn(ACONST_NULL);
                mn.visitInsn(ARETURN);

                mn.visitLabel(popThrowExn);
                mn.visitInsn(POP);
                mn.visitTypeInsn(NEW, "java/util/NoSuchElementException");
                mn.visitInsn(DUP);
                mn.visitMethodInsn(INVOKESPECIAL, "java/util/NoSuchElementException", "<init>", "()V", false);
                mn.visitInsn(ATHROW);
                cn.methods.add(mn);
            }
            {
                MethodNode mn = new MethodNode();
                mn.name = "__free";
                mn.access = ACC_PRIVATE;
                mn.desc = "(I)V";

                Label end = new Label();
                mn.visitVarInsn(ALOAD, 0);
                mn.visitFieldInsn(GETFIELD, cn.name, rn.name, rn.desc);
                mn.visitInsn(DUP);
                mn.visitJumpInsn(IFNULL, end);
                mn.visitVarInsn(ILOAD, 1);
                mn.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                mn.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "remove", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
                mn.visitLabel(end);
                mn.visitInsn(POP);
                mn.visitInsn(RETURN);

                cn.methods.add(mn);
                return new FuncExtern.ModuleFuncExtern(cn, mn, typeNode);
            }
        }
        if (!(matcher = FUNC_PATTERN.matcher(imprt.name)).matches()) {
            return super.resolveFuncImport(cn, imprt);
        }
        String pkg = matcher.group("package");
        String accessor = matcher.group("accessor");
        String methodName = matcher.group("method");
        String desc = matcher.group("desc");
        String className = ("".equals(pkg) ? "java.lang." : pkg) + matcher.group("classname");

        String internalName = className.replace('.', '/');
        Type trueType = Types.methodDesc(typeNode);

        if (".".equals(accessor)) {
            // TODO coerce parameters
            @SuppressWarnings({ "UnusedAssignment", "MismatchedReadAndWriteOfArray" })
            boolean[] coerce = null;
            boolean coerceRet = false;
            if (desc == null) {
                desc = trueType.toString();
            } else {
                Type descType = Type.getType(desc);
                Type[] argTypes = descType.getArgumentTypes();
                Type[] callArgTypes = new Type[argTypes.length];
                coerce = new boolean[argTypes.length];
                for (int i = 0; i < argTypes.length; i++) {
                    if (argTypes[i].getSort() != Type.OBJECT ||
                        argTypes[i].getSort() != Type.ARRAY) {
                        callArgTypes[i] = argTypes[i];
                    } else {
                        callArgTypes[i] = Type.INT_TYPE;
                        coerce[i] = true;
                    }
                }
                Type retType = descType.getReturnType();
                if (retType.getSort() == Type.OBJECT || retType.getSort() == Type.ARRAY) {
                    retType = Type.INT_TYPE;
                    coerceRet = true;
                }
                if (!Type.getMethodType(retType, callArgTypes).equals(trueType)) {
                    throw new IllegalArgumentException("Mismatched descriptor and method type");
                }
            }
            String methodDesc = desc;
            boolean doCoerceRet = coerceRet;
            return new FuncExtern() {
                @Override
                public void emitInvoke(GeneratorAdapter mv) {
                    mv.visitMethodInsn(INVOKESTATIC, internalName, methodName, methodDesc, false);
                    if (doCoerceRet) {
                        mv.loadThis();
                        mv.swap();
                        mv.visitMethodInsn(INVOKEVIRTUAL, internalName, ALLOC_NAME, ALLOC_DESC, false);
                    }
                }

                @Override
                public TypeNode type() {
                    return typeNode;
                }

                @Override
                public void emitGet(GeneratorAdapter mv) {
                    mv.visitLdcInsn(new Handle(H_INVOKESTATIC, internalName, methodName, methodDesc, false));
                    if (doCoerceRet) {
                        mv.visitLdcInsn(new Handle(H_INVOKESTATIC, internalName, ALLOC_NAME, ALLOC_DESC, false));
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
                        mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "filterReturnValue",
                                "(Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/MethodHandle;",
                                false);
                    }
                }
            };
        }
        throw new AssertionError();
    }
}

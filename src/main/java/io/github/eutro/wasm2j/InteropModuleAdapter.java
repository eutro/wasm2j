package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.ImportsVisitor;
import io.github.eutro.jwasm.tree.TypeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Type;
import org.objectweb.asm.commons.GeneratorAdapter;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.Optional;
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
                                                               "(?<desc>" + METHOD_DESC_PATTERN + ")");

    private @Nullable FieldNode referencesNode;
    private @Nullable MethodNode allocNode;
    private @Nullable MethodNode derefNode;

    public InteropModuleAdapter(String internalName) {
        super(internalName);
    }

    @Override
    public @Nullable ImportsVisitor visitImports() {
        return new ImportsVisitor(super.visitImports()) {
            @Override
            public void visitFuncImport(@NotNull String module, @NotNull String name, int type) {
                Optional<FuncExtern> funcExtern = resolveFuncImport(module, name, type);
                if (funcExtern.isPresent()) {
                    FuncExtern e = funcExtern.get();
                    externs.funcs.add(e);
                } else {
                    super.visitFuncImport(module, name, type);
                }
            }
        };
    }

    protected @NotNull Optional<FuncExtern> resolveFuncImport(@NotNull String module, @NotNull String name, int type) {
        Matcher matcher;
        if (!MODULE_NAME.equals(module)) {
            return Optional.empty();
        }
        TypeNode typeNode = getType(type);
        if ("free".equals(name)) {
            if (!Arrays.equals(typeNode.params, new byte[] { I32 }) ||
                !Arrays.equals(typeNode.returns, new byte[] {})) {
                return Optional.empty();
            }
            FieldNode rn = referencesNode();
            allocNode();
            derefNode();
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
            mn.visitMaxs(2, 2);
            return Optional.of(new FuncExtern.ModuleFuncExtern(cn, mn, typeNode));
        }
        if (!(matcher = FUNC_PATTERN.matcher(name)).matches()) {
            return Optional.empty();
        }
        String pkg = matcher.group("package");
        String accessor = matcher.group("accessor");
        String methodName = matcher.group("method");
        String desc = matcher.group("desc");
        String className = ("".equals(pkg) ? "java.lang." : pkg) + matcher.group("classname");

        String internalName = className.replace('.', '/');
        Type trueType = Types.methodDesc(typeNode);

        final int invokeInsn;
        final int handleType;
        final boolean isInterface;
        switch (accessor) {
            case ".":
                invokeInsn = INVOKEVIRTUAL;
                handleType = H_INVOKEVIRTUAL;
                isInterface = false;
                break;
            case "..":
                invokeInsn = INVOKEINTERFACE;
                handleType = H_INVOKEINTERFACE;
                isInterface = true;
                break;
            case "...":
                invokeInsn = INVOKESPECIAL;
                handleType = H_INVOKESPECIAL;
                isInterface = false;
                break;
            default:
                invokeInsn = INVOKESTATIC;
                handleType = H_INVOKESTATIC;
                isInterface = false;
                break;
        }

        boolean[] coerce = null;
        boolean coerceRet = false;
        final Type[] argTypes;
        final Type[] callArgTypes;
        Type descType = Type.getType(desc);
        if ("/".equals(accessor)) {
            argTypes = descType.getArgumentTypes();
        } else {
            Type[] args = descType.getArgumentTypes();
            argTypes = new Type[args.length + 1];
            argTypes[0] = Type.getObjectType(internalName);
            System.arraycopy(args, 0, argTypes, 1, args.length);
        }
        callArgTypes = new Type[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            if (argTypes[i].getSort() != Type.OBJECT &&
                argTypes[i].getSort() != Type.ARRAY) {
                callArgTypes[i] = argTypes[i];
            } else {
                callArgTypes[i] = Type.INT_TYPE;
                if (coerce == null) coerce = new boolean[argTypes.length];
                coerce[i] = true;
            }
        }
        Type retType = descType.getReturnType();
        if (retType.getSort() == Type.OBJECT || retType.getSort() == Type.ARRAY) {
            retType = Type.INT_TYPE;
            coerceRet = true;
        }
        if (!Type.getMethodType(retType, callArgTypes).equals(trueType)) {
            throw new IllegalArgumentException("Mismatched descriptor and method type for " + name +
                                               "\n  expected: " + trueType +
                                               "\n  got: " + Type.getMethodType(retType, callArgTypes));
        }
        boolean[] doCoerce = coerce;
        boolean doCoerceRet = coerceRet;
        return Optional.of(new FuncExtern() {
            @Override
            public void emitInvoke(GeneratorAdapter mv) {
                if (doCoerce != null) {
                    int[] locals = new int[doCoerce.length];
                    for (int i = doCoerce.length - 1; i >= 0; i--) {
                        mv.storeLocal(locals[i] = mv.newLocal(callArgTypes[i]));
                    }
                    for (int i = 0; i < locals.length; i++) {
                        if (doCoerce[i]) mv.loadThis();
                        mv.loadLocal(locals[i]);
                        if (doCoerce[i]) invokeDeref(mv);
                        mv.checkCast(argTypes[i]);
                    }
                }
                mv.visitMethodInsn(invokeInsn, internalName, methodName, desc, isInterface);
                if (doCoerceRet) {
                    mv.loadThis();
                    mv.swap();
                    invokeAlloc(mv);
                }
            }

            @Override
            public TypeNode type() {
                return typeNode;
            }

            @Override
            public void emitGet(GeneratorAdapter mv) {
                mv.visitLdcInsn(new Handle(handleType, internalName, methodName, desc, isInterface));
                if (doCoerce != null) {
                    derefHandle(mv);
                    Type mhType = Type.getType(MethodHandle.class);
                    int derefHandle = mv.newLocal(mhType);
                    mv.storeLocal(derefHandle);
                    mv.push(0);
                    mv.push(doCoerce.length);
                    mv.newArray(mhType);
                    for (int i = 0; i < doCoerce.length; i++) {
                        if (doCoerce[i]) {
                            mv.dup();
                            mv.push(i);
                            mv.loadLocal(derefHandle);
                            mv.arrayStore(mhType);
                        }
                    }
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "filterArguments",
                            "(Ljava/lang/invoke/MethodHandle;I[Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/MethodHandle;",
                            false);
                }
                if (doCoerceRet) {
                    allocHandle(mv);
                    mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "filterReturnValue",
                            "(Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/MethodHandle;",
                            false);
                }
            }
        });
    }

    private @NotNull FieldNode referencesNode() {
        if (referencesNode == null) {
            referencesNode = new FieldNode(ACC_PRIVATE,
                    "__references",
                    "Ljava/util/Map;",
                    "Ljava/util/Map<Ljava/lang/Integer;Ljava/lang/Object;>;",
                    null);
            cn.fields.add(referencesNode);
        }
        return referencesNode;
    }

    private @NotNull MethodNode derefNode() {
        if (derefNode == null) {
            FieldNode rn = referencesNode();

            derefNode = new MethodNode();
            derefNode.name = "__deref";
            derefNode.access = ACC_PRIVATE;
            derefNode.desc = "(I)Ljava/lang/Object;";

            Label popThrowExn = new Label();
            Label retNull = new Label();
            derefNode.visitVarInsn(ALOAD, 0);
            derefNode.visitFieldInsn(GETFIELD, cn.name, rn.name, rn.desc);
            derefNode.visitInsn(DUP);
            derefNode.visitJumpInsn(IFNULL, popThrowExn);

            derefNode.visitVarInsn(ILOAD, 1);
            derefNode.visitInsn(DUP);
            derefNode.visitJumpInsn(IFEQ, retNull);
            derefNode.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            derefNode.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            derefNode.visitInsn(DUP);
            derefNode.visitJumpInsn(IFNULL, popThrowExn);
            derefNode.visitInsn(ARETURN);

            derefNode.visitLabel(retNull);
            derefNode.visitInsn(ACONST_NULL);
            derefNode.visitInsn(ARETURN);

            derefNode.visitLabel(popThrowExn);
            derefNode.visitInsn(POP);
            derefNode.visitTypeInsn(NEW, "java/util/NoSuchElementException");
            derefNode.visitInsn(DUP);
            derefNode.visitMethodInsn(INVOKESPECIAL, "java/util/NoSuchElementException", "<init>", "()V", false);
            derefNode.visitInsn(ATHROW);
            derefNode.visitMaxs(3, 2);
            cn.methods.add(derefNode);
        }
        return derefNode;
    }

    private @NotNull MethodNode allocNode() {
        if (allocNode == null) {
            FieldNode rn = referencesNode();

            FieldNode ct = new FieldNode(ACC_PRIVATE,
                    "__count",
                    "I",
                    null,
                    null);
            cn.fields.add(ct);
            allocNode = new MethodNode();
            allocNode.name = "__alloc";
            allocNode.access = ACC_PRIVATE;
            allocNode.desc = "(Ljava/lang/Object;)I";

            Label endCreate = new Label();
            allocNode.visitVarInsn(ALOAD, 0);
            allocNode.visitInsn(DUP);
            allocNode.visitInsn(DUP);
            allocNode.visitFieldInsn(GETFIELD, cn.name, ct.name, ct.desc);
            allocNode.visitInsn(ICONST_1);
            allocNode.visitInsn(IADD);
            allocNode.visitInsn(DUP_X1);
            allocNode.visitFieldInsn(PUTFIELD, cn.name, ct.name, ct.desc);
            allocNode.visitInsn(DUP_X1);
            allocNode.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            allocNode.visitInsn(SWAP);
            allocNode.visitFieldInsn(GETFIELD, cn.name, rn.name, rn.desc);
            allocNode.visitInsn(DUP);
            allocNode.visitJumpInsn(IFNONNULL, endCreate);

            allocNode.visitInsn(POP);
            allocNode.visitVarInsn(ALOAD, 0);
            allocNode.visitTypeInsn(NEW, "java/util/HashMap");
            allocNode.visitInsn(DUP);
            allocNode.visitMethodInsn(INVOKESPECIAL, "java/util/HashMap", "<init>", "()V", false);
            allocNode.visitInsn(DUP_X1);
            allocNode.visitFieldInsn(PUTFIELD, cn.name, rn.name, rn.desc);

            allocNode.visitLabel(endCreate);
            allocNode.visitInsn(SWAP);
            allocNode.visitVarInsn(ALOAD, 1);
            allocNode.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "put", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;", true);
            allocNode.visitInsn(POP);
            allocNode.visitInsn(IRETURN);
            allocNode.visitMaxs(5, 2);
            cn.methods.add(allocNode);
        }
        return allocNode;
    }

    private void invokeAlloc(GeneratorAdapter mv) {
        mv.visitMethodInsn(INVOKEVIRTUAL, cn.name, allocNode().name, allocNode().desc, false);
    }

    private void invokeDeref(GeneratorAdapter mv) {
        mv.visitMethodInsn(INVOKEVIRTUAL, cn.name, derefNode().name, derefNode().desc, false);
    }

    private void allocHandle(GeneratorAdapter mv) {
        thisHandle(mv, allocNode().name, allocNode().desc);
    }

    private void derefHandle(GeneratorAdapter mv) {
        thisHandle(mv, derefNode().name, derefNode().desc);
    }

    private void thisHandle(GeneratorAdapter mv, String derefName, String derefDesc) {
        mv.visitLdcInsn(new Handle(H_INVOKEVIRTUAL, cn.name, derefName, derefDesc, false));
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
}

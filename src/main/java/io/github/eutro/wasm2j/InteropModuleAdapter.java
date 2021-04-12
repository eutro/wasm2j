package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.ExportsVisitor;
import io.github.eutro.jwasm.ImportsVisitor;
import io.github.eutro.jwasm.Opcodes;
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

    public static final Pattern UNQUALIFIED_NAME_PATTERN = Pattern.compile("(?:[^.;\\[/]+?)");
    public static final Pattern METHOD_NAME_PATTERN = Pattern.compile("(?:[^.;\\[/<>()]+?)");
    public static final Pattern INTERNAL_NAME_PATTERN = Pattern.compile("(?:(?:" + UNQUALIFIED_NAME_PATTERN + "/)*" +
                                                                        UNQUALIFIED_NAME_PATTERN + ")");
    public static final Pattern DESC_PATTERN = Pattern.compile("(?:(?:\\[*)" +
                                                               "(?:(?:[BCDFIJSZ])|" +
                                                               "L" + INTERNAL_NAME_PATTERN + ";))");
    public static final Pattern METHOD_DESC_PATTERN = Pattern.compile("(?:\\(" + DESC_PATTERN + "*\\)(?:" + DESC_PATTERN + "|V))");
    public static final Pattern IMPORT_FUNC_PATTERN = Pattern.compile("(?<package>(?:" + UNQUALIFIED_NAME_PATTERN + "\\.)*)" +
                                                                      "(?<classname>" + UNQUALIFIED_NAME_PATTERN + ")" +
                                                                      "(?<accessor>[.#^/@~])" +
                                                                      "(?<method>" + METHOD_NAME_PATTERN + ")" +
                                                                      "(?<desc>" + METHOD_DESC_PATTERN + ")");
    public static final Pattern EXPORT_FUNC_PATTERN = Pattern.compile("(?<method>" + METHOD_NAME_PATTERN + ")" +
                                                                      "((?<move>\\*)?(?<desc>" + METHOD_DESC_PATTERN + "))?");

    private @Nullable FieldNode referencesNode;
    private @Nullable MethodNode allocNode;
    private @Nullable MethodNode derefNode;
    private @Nullable MethodNode freeNode;

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

    @Override
    public @Nullable ExportsVisitor visitExports() {
        int inFuncsC = externs.funcs.size() - funcs.size();
        return new ExportsVisitor(super.visitExports()) {
            @Override
            public void visitExport(@NotNull String name, byte type, int index) {
                if (type == Opcodes.EXPORTS_FUNC) {
                    Matcher matcher = EXPORT_FUNC_PATTERN.matcher(name);
                    if (!matcher.matches()) {
                        throw new IllegalArgumentException("Invalid func export");
                    }
                    String methodName = matcher.group("method");
                    String desc = matcher.group("desc");
                    if (desc == null) {
                        super.visitExport(methodName, type, index);
                        return;
                    }
                    boolean move = matcher.group("move") != null;

                    MethodNode method = funcs.get(index - inFuncsC);
                    Type original = Type.getMethodType(method.desc);
                    Type override = Type.getMethodType(desc);
                    Type[] originalArgs = original.getArgumentTypes();
                    Type[] overrideArgs = override.getArgumentTypes();
                    Type originalRet = original.getReturnType();
                    Type overrideRet = override.getReturnType();
                    if (originalArgs.length != overrideArgs.length) {
                        throw new IllegalArgumentException("Bad arity for method descriptor");
                    }

                    if (Arrays.equals(originalArgs, overrideArgs)) {
                        if (originalRet.equals(overrideRet)) {
                            super.visitExport(methodName, type, index);
                            return;
                        }
                        method.name = methodName + "0";
                    } else {
                        method.name = methodName;
                    }

                    MethodNode mn = new MethodNode();
                    cn.methods.add(mn);
                    mn.name = methodName;
                    mn.access = ACC_PUBLIC;
                    mn.desc = override.getDescriptor();

                    GeneratorAdapter ga = new GeneratorAdapter(mn, mn.access, mn.name, mn.desc);
                    ga.loadThis();
                    for (int arg = 0; arg < overrideArgs.length; arg++) {
                        int finalArg = arg;
                        emitAlloc(ga, originalArgs[arg], overrideArgs[arg], () -> ga.loadArg(finalArg));
                    }
                    ga.visitMethodInsn(INVOKEVIRTUAL, cn.name, method.name, method.desc, false);
                    if (!overrideRet.equals(originalRet)) {
                        if (overrideRet.getSort() == Type.OBJECT ||
                            overrideRet.getSort() == Type.ARRAY) {
                            if (!Type.INT_TYPE.equals(originalRet)) {
                                throw new IllegalArgumentException("Overridden reference type argument representation must be I32");
                            }
                            if (move) {
                                ga.dup();
                            }
                            ga.loadThis();
                            ga.swap();
                            invokeDeref(ga);
                            ga.checkCast(overrideRet);
                            if (move) {
                                ga.swap();
                                ga.loadThis();
                                ga.swap();
                                ga.visitMethodInsn(INVOKEVIRTUAL, cn.name, freeNode().name, freeNode().desc, false);
                            }
                        }
                    }
                    ga.returnValue();
                    ga.visitMaxs(
                            Math.min((original.getArgumentsAndReturnSizes() >> 2) + 2, 3),
                            (override.getArgumentsAndReturnSizes() >> 2) + 1
                    );
                    ga.visitEnd();
                } else {
                    super.visitExport(name, type, index);
                }
            }
        };
    }

    private void emitAlloc(GeneratorAdapter ga, Type wasmType, Type javaType, Runnable emit) {
        if (wasmType.equals(javaType)) {
            emit.run();
            return;
        }
        if (javaType.getSort() == Type.OBJECT ||
            javaType.getSort() == Type.ARRAY) {
            if (!Type.INT_TYPE.equals(wasmType)) {
                throw new IllegalArgumentException("Overridden reference type argument representation must be I32");
            }
            ga.loadThis();
            emit.run();
            invokeAlloc(ga);
        } else {
            emit.run();
            ga.cast(javaType, wasmType);
        }
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
            return Optional.of(new FuncExtern.ModuleFuncExtern(cn, freeNode(), typeNode));
        }

        if ("memory".equals(name)) {
            if (!Arrays.equals(typeNode.params, new byte[] {}) ||
                !Arrays.equals(typeNode.returns, new byte[] { I32 })) {
                return Optional.empty();
            }
            return Optional.of(new FuncExtern() {
                @Override
                public void emitInvoke(GeneratorAdapter mv) {
                    FieldNode fn = mems.get(0);
                    mv.loadThis();
                    mv.dup();
                    mv.visitFieldInsn(GETFIELD, cn.name, fn.name, fn.desc);
                    invokeAlloc(mv);
                }

                @Override
                public TypeNode type() {
                    return typeNode;
                }

                @Override
                public void emitGet(GeneratorAdapter mv) {
                    FieldNode fn = mems.get(0);
                    thisHandle(mv, new Handle(H_GETFIELD, cn.name, fn.name, fn.desc, false));
                    allocHandle(mv);
                    filterReturn(mv);
                }
            });
        }

        if (!(matcher = IMPORT_FUNC_PATTERN.matcher(name)).matches()) {
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
        final boolean isField;
        switch (accessor) {
            case ".":
                invokeInsn = INVOKEVIRTUAL;
                handleType = H_INVOKEVIRTUAL;
                isInterface = false;
                isField = false;
                break;
            case "#":
                invokeInsn = INVOKEINTERFACE;
                handleType = H_INVOKEINTERFACE;
                isInterface = true;
                isField = false;
                break;
            case "^":
                invokeInsn = INVOKESPECIAL;
                handleType = H_INVOKESPECIAL;
                isInterface = false;
                isField = false;
                break;
            case "@":
                invokeInsn = GETFIELD;
                handleType = H_GETFIELD;
                isInterface = false;
                isField = true;
                break;
            case "~":
                invokeInsn = GETSTATIC;
                handleType = H_GETSTATIC;
                isInterface = false;
                isField = true;
                break;
            default:
                invokeInsn = INVOKESTATIC;
                handleType = H_INVOKESTATIC;
                isInterface = false;
                isField = false;
                break;
        }

        boolean[] coerce = null;
        boolean coerceRet = false;
        final Type[] argTypes;
        final Type[] callArgTypes;
        Type descType = Type.getType(desc);
        if ("/".equals(accessor) || "~".equals(accessor)) {
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
                        if (doCoerce[i]) {
                            invokeDeref(mv);
                            mv.checkCast(argTypes[i]);
                        }
                    }
                }
                if (isField) {
                    mv.visitFieldInsn(invokeInsn, internalName, methodName, desc.substring(2));
                } else {
                    mv.visitMethodInsn(invokeInsn, internalName, methodName, desc, isInterface);
                }
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
                mv.visitLdcInsn(new Handle(handleType, internalName, methodName, isField ? desc.substring(2) : desc, isInterface));
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
                    filterReturn(mv);
                }
            }
        });
    }

    private void filterReturn(GeneratorAdapter mv) {
        mv.visitMethodInsn(INVOKESTATIC, "java/lang/invoke/MethodHandles", "filterReturnValue",
                "(Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodHandle;)Ljava/lang/invoke/MethodHandle;",
                false);
    }

    @NotNull
    private MethodNode freeNode() {
        if (freeNode == null) {
            freeNode = new MethodNode();
            freeNode.name = "__free";
            freeNode.access = ACC_PRIVATE;
            freeNode.desc = "(I)V";

            Label end = new Label();
            freeNode.visitVarInsn(ALOAD, 0);
            freeNode.visitFieldInsn(GETFIELD, cn.name, referencesNode().name, referencesNode().desc);
            freeNode.visitInsn(DUP);
            freeNode.visitJumpInsn(IFNULL, end);
            freeNode.visitVarInsn(ILOAD, 1);
            freeNode.visitMethodInsn(INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
            freeNode.visitMethodInsn(INVOKEINTERFACE, "java/util/Map", "remove", "(Ljava/lang/Object;)Ljava/lang/Object;", true);
            freeNode.visitLabel(end);
            freeNode.visitInsn(POP);
            freeNode.visitInsn(RETURN);

            cn.methods.add(freeNode);
            freeNode.visitMaxs(2, 2);
        }
        return freeNode;
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
            derefNode.signature = "<T:Ljava/lang/Object;>(I)TT;";

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
            derefNode.visitLdcInsn("Attempted to dereference an invalid pointer");
            derefNode.visitMethodInsn(INVOKESPECIAL, "java/util/NoSuchElementException", "<init>", "(Ljava/lang/String;)V", false);
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
            Label inc = new Label();
            allocNode.visitVarInsn(ALOAD, 0);
            allocNode.visitInsn(DUP);
            allocNode.visitInsn(DUP);
            allocNode.visitFieldInsn(GETFIELD, cn.name, ct.name, ct.desc);
            allocNode.visitLabel(inc);
            allocNode.visitInsn(ICONST_1);
            allocNode.visitInsn(IADD);
            allocNode.visitInsn(DUP);
            allocNode.visitJumpInsn(IFEQ, inc);
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
        thisHandle(mv, new Handle(H_INVOKEVIRTUAL, cn.name, derefName, derefDesc, false));
    }

    private void thisHandle(GeneratorAdapter mv, Handle value) {
        mv.visitLdcInsn(value);
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

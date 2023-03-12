package io.github.eutro.wasm2j.core.passes.convert;

import io.github.eutro.wasm2j.core.ext.CommonExts;
import io.github.eutro.wasm2j.core.ext.JavaExts;
import io.github.eutro.wasm2j.core.ext.MetadataState;
import io.github.eutro.wasm2j.core.ops.CommonOps;
import io.github.eutro.wasm2j.core.ops.JavaOps;
import io.github.eutro.wasm2j.core.ops.OpKey;
import io.github.eutro.wasm2j.core.passes.IRPass;
import io.github.eutro.wasm2j.core.ssa.*;
import io.github.eutro.wasm2j.core.util.F;
import io.github.eutro.wasm2j.core.util.IRUtils;
import io.github.eutro.wasm2j.core.util.ValueGetter;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.util.*;

/**
 * Convert a function to a function which constructs a {@link MethodHandle} of the former,
 * optionally binding certain arguments early (by keeping them "free" in the handle).
 * <p>
 * This can very easily fail for any number of reasons, mostly due to the small number of
 * primitive instructions implemented, and as such this will return {@code null} in case of failure.
 */
public class Handlify implements IRPass<Function, @Nullable Function> {
    private static final JClass METHOD_HANDLES_CLASS = JClass.emptyFromJava(MethodHandles.class);
    private static final JClass.JavaMethod MH_CONSTANT = METHOD_HANDLES_CLASS.lookupMethod("constant",
            Class.class, Object.class);
    private static final JClass.JavaMethod MH_IDENTITY = METHOD_HANDLES_CLASS.lookupMethod("identity",
            Class.class);
    private static final JClass.JavaMethod MH_PERMUTE_ARGUMENTS = METHOD_HANDLES_CLASS.lookupMethod(
            "permuteArguments",
            MethodHandle.class,
            MethodType.class,
            int[].class);
    private static final JClass.JavaMethod MH_COLLECT_ARGUMENTS = METHOD_HANDLES_CLASS.lookupMethod(
            "collectArguments",
            MethodHandle.class,
            int.class,
            MethodHandle.class);
    private static final JClass.JavaMethod MH_AS_TYPE = IRUtils.METHOD_HANDLE_CLASS.lookupMethod(
            "asType",
            MethodType.class);
    private static final JClass.JavaMethod MH_TYPE = IRUtils.METHOD_HANDLE_CLASS.lookupMethod(
            "type");
    private static final JClass.JavaMethod MTY_WITH_RETURN = IRUtils.MTY_CLASS.lookupMethod(
            "changeReturnType",
            Class.class);
    private static final JClass.JavaMethod MTY_PARAM_TY = IRUtils.MTY_CLASS.lookupMethod(
            "parameterType",
            int.class);


    private final BitSet keepFree;

    /**
     * Construct a new {@link Handlify} that will keep no arguments kept free.
     */
    public Handlify() {
        this(new BitSet());
    }

    /**
     * Construct a new {@link Handlify} that will keep the {@link BitSet#set(int) set} arguments free.
     *
     * @param keepFree Which function arguments should be kept free in the computed handle.
     */
    public Handlify(BitSet keepFree) {
        this.keepFree = keepFree;
    }

    @Override
    public @Nullable Function run(Function function) {
        // no control flow
        if (function.blocks.size() != 1) return null;
        BasicBlock root = function.blocks.get(0);
        if (root.getControl().insn().op != CommonOps.RETURN) return null;
        if (root.getControl().insn().args().size() > 1) return null;

        MetadataState ms = function.getExtOrThrow(CommonExts.METADATA_STATE);
        try {
            ms.ensureValid(function, MetadataState.JTYPES_INFERRED);
        } catch (RuntimeException ignored) {
            return null; // well then
        }

        // collect the method handles for all the instructions
        List<Cont> insnHandles = new ArrayList<>();
        for (Effect effect : root.getEffects()) {
            if (effect.insn().op.key == CommonOps.ARG) continue;
            List<Var> assignsTo = effect.getAssignsTo();
            if (assignsTo.size() > 1) return null;
            ValueGetter handle = getHandleFor(effect.insn());
            if (handle == null) return null;
            insnHandles.add(new Cont(assignsTo.size() == 0 ? null : assignsTo.get(0), effect.insn(), handle));
        }

        Function func = new Function();
        IRBuilder ib = new IRBuilder(func, func.newBb());

        // build up the function (as a continuation) in reverse
        Var k = Objects.requireNonNull(getHandleFor(root.getControl().insn())).get(ib);
        List<Var> currentArgsList = new ArrayList<>();
        Map<Var, Integer> currentArgIdcs = new HashMap<>();
        Type returnType;
        if (root.getControl().insn().args().size() == 1) {
            Var retVar = root.getControl().insn().args().get(0);
            returnType = retVar.getExtOrThrow(JavaExts.TYPE);
            currentArgsList.add(retVar);
            currentArgIdcs.put(retVar, 0);
        } else {
            returnType = Type.VOID_TYPE;
        }

        ListIterator<Cont> iter = insnHandles.listIterator(insnHandles.size());
        while (iter.hasPrevious()) {
            Cont cont = iter.previous();
            int idx = currentArgIdcs.getOrDefault(cont.var, currentArgsList.size());
            Var handle = cont.handle.get(ib);
            boolean isDropped = idx == currentArgsList.size();
            if (isDropped && cont.var != null) {
                // we're dropping an actual value, do this by casting to void
                handle = dropResult(ib, handle);
            }
            Var idxV = ib.insert(CommonOps.constant(idx), "i");
            if (!isDropped) {
                handle = ib.insert(JavaOps.INVOKE.create(MH_AS_TYPE)
                                .insn(handle, ib.insert(JavaOps.INVOKE.create(MTY_WITH_RETURN)
                                                .insn(ib.insert(JavaOps.INVOKE.create(MH_TYPE).insn(handle), "mhTy"),
                                                        ib.insert(JavaOps.INVOKE.create(MTY_PARAM_TY)
                                                                        .insn(ib.insert(JavaOps.INVOKE.create(MH_TYPE).insn(k), "kTy"),
                                                                                idxV),
                                                                "argTy")),
                                        "castTy")),
                        "castHandle");
            }
            k = ib.insert(JavaOps.INVOKE.create(MH_COLLECT_ARGUMENTS)
                            .insn(k, idxV, handle),
                    "k");
            boolean needsPermute = false;
            for (Var arg : cont.insn.args()) {
                if (currentArgIdcs.containsKey(arg)) {
                    needsPermute = true;
                    break;
                }
            }

            if (needsPermute) {
                List<Var> permutation = new ArrayList<>(currentArgsList);
                if (!isDropped) {
                    permutation.remove(idx);
                }
                permutation.addAll(idx, cont.insn.args());
                int[] permutationIdcs = new int[permutation.size()];
                currentArgsList.clear();
                currentArgIdcs.clear();
                for (int i = 0; i < permutation.size(); i++) {
                    Var var = permutation.get(i);
                    Integer mbPermIdx = currentArgIdcs.get(var);
                    int permIdx;
                    if (mbPermIdx == null) {
                        currentArgIdcs.put(var, permIdx = currentArgIdcs.size());
                        currentArgsList.add(var);
                    } else {
                        permIdx = mbPermIdx;
                    }
                    permutationIdcs[i] = permIdx;
                }
                List<Type> types = new ArrayList<>();
                for (Var var : currentArgsList) {
                    types.add(var.getExtOrThrow(JavaExts.TYPE));
                }
                k = permuteHandle(ib, k, types, returnType, permutationIdcs);
            } else {
                if (!isDropped) {
                    currentArgsList.remove(idx);
                }
                currentArgsList.addAll(idx, cont.insn.args());
                currentArgIdcs.clear();
                for (int i = 0; i < currentArgsList.size(); i++) {
                    Var var = currentArgsList.get(i);
                    currentArgIdcs.put(var, i);
                }
            }
        }

        // finally, the only remaining variables should be the arguments
        int[] permArray = new int[currentArgsList.size()];
        for (int i = 0; i < currentArgsList.size(); i++) {
            Var var = currentArgsList.get(i);
            Effect assignedAt = var.getExtOrThrow(CommonExts.ASSIGNED_AT);
            permArray[i] = CommonOps.ARG.cast(assignedAt.insn().op).arg;
        }
        JClass.JavaMethod method = function.getExtOrThrow(JavaExts.FUNCTION_METHOD);
        k = permuteHandle(ib, k, method.getParamTys(), returnType, permArray);

        // bind the "free" arguments
        int argIdx = keepFree.cardinality();
        for (int i = keepFree.previousSetBit(Integer.MAX_VALUE); i != -1; i = keepFree.previousSetBit(i - 1)) {
            argIdx--;
            Type argTy = method.getParamTys().get(i);
            k = ib.insert(JavaOps.INVOKE.create(MH_COLLECT_ARGUMENTS)
                            .insn(k,
                                    ib.insert(CommonOps.constant(i), "argIdx"),
                                    ib.insert(JavaOps.INVOKE.create(MH_CONSTANT)
                                                    .insn(ib.insert(IRUtils.loadClass(argTy), "argTy"),
                                                            boxed(ib, argTy, ib.insert(CommonOps.ARG.create(argIdx).insn(),
                                                                    "arg" + argIdx))),
                                            "constArg")),
                    "k");
        }

        ib.insertCtrl(CommonOps.RETURN.insn(k).jumpsTo());

        return func;
    }

    private static Var dropResult(IRBuilder ib, Var handle) {
        return ib.insert(JavaOps.INVOKE.create(MH_AS_TYPE)
                        .insn(handle,
                                ib.insert(JavaOps.INVOKE.create(MTY_WITH_RETURN)
                                                .insn(ib.insert(JavaOps.INVOKE.create(MH_TYPE).insn(handle), "mhTy"),
                                                        ib.insert(IRUtils.loadClass(Type.VOID_TYPE), "void")),
                                        "voided")),
                "dropping");
    }

    private Var permuteHandle(IRBuilder ib, Var k, List<Type> paramTypes, Type returnType, int[] permutationIdcs) {
        if (paramTypes.size() == permutationIdcs.length) doPermute:{
            for (int i = 0; i < permutationIdcs.length; i++) {
                if (permutationIdcs[i] != i) break doPermute;
            }
            return k;
        }

        Var paramTys = ib.insert(JavaOps
                        .insns(new TypeInsnNode(Opcodes.ANEWARRAY, Type.getInternalName(Class.class)))
                        .insn(ib.insert(CommonOps.constant(paramTypes.size()), "ptLen")),
                "pTys");
        for (int i = 0; i < paramTypes.size(); i++) {
            ib.insert(JavaOps.ARRAY_SET.create()
                    .insn(paramTys, ib.insert(CommonOps.constant(i), "i"),
                            ib.insert(IRUtils.loadClass(paramTypes.get(i)), "pTy"))
                    .assignTo());
        }

        Var order = ib.insert(JavaOps
                        .insns(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_INT))
                        .insn(ib.insert(CommonOps.constant(permutationIdcs.length), "odLen")),
                "order");
        for (int i = 0; i < permutationIdcs.length; i++) {
            ib.insert(JavaOps.ARRAY_SET.create()
                    .insn(order, ib.insert(CommonOps.constant(i), "i"),
                            ib.insert(CommonOps.constant(permutationIdcs[i]), "ty"))
                    .assignTo());
        }
        return ib.insert(JavaOps.INVOKE
                        .create(MH_PERMUTE_ARGUMENTS)
                        .insn(k,
                                ib.insert(JavaOps.INVOKE
                                                .create(IRUtils.MTY_METHOD_TYPE)
                                                .insn(ib.insert(IRUtils.loadClass(returnType), "retTy"), paramTys),
                                        "mty"),
                                order),
                "permuted");
    }

    private final Map<OpKey, F<Insn, @Nullable ValueGetter>> CONVERTERS = new HashMap<>();

    {
        CONVERTERS.put(CommonOps.IDENTITY.key, insn -> {
            switch (insn.args().size()) {
                case 1:
                    return ib -> {
                        Var arg = insn.args().get(0);
                        Type type = arg.getExtOrThrow(JavaExts.TYPE);
                        return ib.insert(JavaOps.INVOKE.create(MH_IDENTITY)
                                        .insn(ib.insert(IRUtils.loadClass(type), "ty")),
                                "id");
                    };
                case 0:
                    return ib -> dropResult(ib, ib.insert(JavaOps.INVOKE.create(MH_CONSTANT)
                                    .insn(ib.insert(IRUtils.loadClass(Type.getType(Object.class)), "ty"),
                                            ib.insert(CommonOps.constant(null), "null")),
                            "empty"));
                default:
                    return null;
            }
        });
        CONVERTERS.put(CommonOps.RETURN.key, CONVERTERS.get(CommonOps.IDENTITY.key));
        CONVERTERS.put(JavaOps.INVOKE, insn -> ib -> ib.insert(JavaOps.HANDLE_OF
                        .create(JavaOps.INVOKE.cast(insn.op).arg).insn(),
                "invokeHandle"));
        CONVERTERS.put(JavaOps.GET_FIELD, insn -> ib -> ib.insert(JavaOps.HANDLE_OF
                .create(JavaOps.GET_FIELD.cast(insn.op).arg.getter()).insn(),
                "getterHandle"));
        CONVERTERS.put(JavaOps.PUT_FIELD, insn -> ib -> ib.insert(JavaOps.HANDLE_OF
                        .create(JavaOps.PUT_FIELD.cast(insn.op).arg.setter()).insn(),
                "setterHandle"));
        CONVERTERS.put(JavaOps.HANDLE_OF, insn -> ib -> ib.insert(JavaOps.INVOKE.create(MH_CONSTANT)
                        .insn(ib.insert(CommonOps.constant(Type.getType(MethodHandle.class)), "mhClass"),
                                ib.insert(insn.op.insn(), "handle")),
                "constHandle"));
        CONVERTERS.put(JavaOps.ARRAY_GET, insn -> ib -> ib.insert(JavaOps.INVOKE
                        .create(METHOD_HANDLES_CLASS.lookupMethod("arrayElementGetter", Class.class))
                        .insn(ib.insert(IRUtils.loadClass(insn.args().get(0).getExtOrThrow(JavaExts.TYPE)), "ty")),
                "aeg"));
        CONVERTERS.put(JavaOps.ARRAY_SET, insn -> ib -> ib.insert(JavaOps.INVOKE
                        .create(METHOD_HANDLES_CLASS.lookupMethod("arrayElementSetter", Class.class))
                        .insn(ib.insert(IRUtils.loadClass(insn.args().get(0).getExtOrThrow(JavaExts.TYPE)), "ty")),
                "aes"));
        CONVERTERS.put(JavaOps.INSNS, insn -> {
            InsnList il = JavaOps.INSNS.cast(insn.op).arg;
            if (il.size() != 1) return null;
            AbstractInsnNode in = il.getFirst();
            switch (in.getOpcode()) {
                case Opcodes.INVOKEVIRTUAL:
                case Opcodes.INVOKESPECIAL:
                case Opcodes.INVOKESTATIC:
                case Opcodes.INVOKEINTERFACE: {
                    MethodInsnNode min = (MethodInsnNode) in;
                    return ib -> ib.insert(CommonOps.constant(new Handle(
                            getHandleTag(in.getOpcode()),
                            min.owner, min.name, min.desc, min.itf
                    )), "handle");
                }
                case Opcodes.GETSTATIC:
                case Opcodes.PUTSTATIC:
                case Opcodes.GETFIELD:
                case Opcodes.PUTFIELD: {
                    FieldInsnNode fin = (FieldInsnNode) in;
                    return ib -> ib.insert(CommonOps.constant(new Handle(
                            getHandleTag(in.getOpcode()),
                            fin.owner, fin.name, fin.desc, false
                    )), "handle");
                }
                case Opcodes.ARRAYLENGTH:
                    return ib -> ib.insert(CommonOps.constant(new Handle(Opcodes.H_INVOKESTATIC,
                            Type.getInternalName(Array.class),
                            "getLength",
                            "(Ljava/lang/Object;)I",
                            false)),
                            "arraylength");
                case Opcodes.POP:
                    return ib -> dropResult(ib, ib.insert(JavaOps.INVOKE.create(MH_IDENTITY)
                                    .insn(ib.insert(IRUtils.loadClass(Type.getType(Object.class)), "ty")),
                            "id"));
            }
            return null;
        });
        CONVERTERS.put(CommonOps.CONST, insn -> ib -> {
            Type constType = insn.getExtOrThrow(CommonExts.OWNING_EFFECT)
                    .getAssignsTo().get(0)
                    .getExtOrThrow(JavaExts.TYPE);
            return ib.insert(JavaOps.INVOKE
                            .create(METHOD_HANDLES_CLASS.lookupMethod(
                                    "constant",
                                    Class.class, Object.class))
                            .insn(ib.insert(IRUtils.loadClass(constType), "constTy"),
                                    boxed(ib, constType, ib.insert(insn.op.insn(), "constVal"))),
                    "constHandle");
        });
        CONVERTERS.put(JavaOps.THIS.key, CONVERTERS.get(CommonOps.CONST));
    }

    private static int getHandleTag(int opcode) {
        switch (opcode) {
            case Opcodes.GETSTATIC:
                return Opcodes.H_GETSTATIC;
            case Opcodes.PUTSTATIC:
                return Opcodes.H_PUTSTATIC;
            case Opcodes.GETFIELD:
                return Opcodes.H_GETFIELD;
            case Opcodes.PUTFIELD:
                return Opcodes.H_PUTFIELD;
            case Opcodes.INVOKEVIRTUAL:
                return Opcodes.H_INVOKEVIRTUAL;
            case Opcodes.INVOKESPECIAL:
                return Opcodes.H_INVOKESPECIAL;
            case Opcodes.INVOKESTATIC:
                return Opcodes.H_INVOKESTATIC;
            case Opcodes.INVOKEINTERFACE:
                return Opcodes.H_INVOKEINTERFACE;
        }
        throw new IllegalArgumentException();
    }

    private Var boxed(IRBuilder ib, Type type, Var maybePrim) {
        Class<?> boxedClass;
        switch (type.getSort()) {
            case Type.OBJECT:
            case Type.ARRAY:
                return maybePrim;
            case Type.BOOLEAN:
                boxedClass = Boolean.class;
                break;
            case Type.CHAR:
                boxedClass = Character.class;
                break;
            case Type.BYTE:
                boxedClass = Byte.class;
                break;
            case Type.SHORT:
                boxedClass = Short.class;
                break;
            case Type.INT:
                boxedClass = Integer.class;
                break;
            case Type.FLOAT:
                boxedClass = Float.class;
                break;
            case Type.LONG:
                boxedClass = Long.class;
                break;
            case Type.DOUBLE:
                boxedClass = Double.class;
                break;
            default:
                throw new IllegalArgumentException();
        }
        return ib.insert(JavaOps.INVOKE
                        .create(new JClass.JavaMethod(
                                new JClass(Type.getInternalName(boxedClass)),
                                "valueOf",
                                Type.getMethodDescriptor(Type.getType(boxedClass), type),
                                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
                        ))
                        .insn(maybePrim),
                "boxed");
    }

    private @Nullable ValueGetter getHandleFor(Insn insn) {
        F<Insn, @Nullable ValueGetter> cc = CONVERTERS.get(insn.op.key);
        if (cc == null) return null;
        return cc.apply(insn);
    }

    private static class Cont {
        final Var var;
        final Insn insn;
        final ValueGetter handle;

        public Cont(Var var, Insn insn, ValueGetter handle) {
            this.var = var;
            this.insn = insn;
            this.handle = handle;
        }
    }
}

package io.github.eutro.wasm2j.passes.meta;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.OpKey;
import io.github.eutro.wasm2j.passes.InPlaceIrPass;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.util.F;
import io.github.eutro.wasm2j.util.Preorder;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.lang.reflect.Array;
import java.util.*;

import static io.github.eutro.wasm2j.passes.meta.InferTypes.FuncType.withArity;
import static org.objectweb.asm.Opcodes.*;

public abstract class InferTypes<Ty> implements InPlaceIrPass<Function> {
    protected final Ext<Ty> tyExt;

    public InferTypes(Ext<Ty> tyExt) {
        this.tyExt = tyExt;
    }

    @Override
    public void runInPlace(Function func) {
        for (BasicBlock block : new Preorder<>(func.blocks.get(0), $ -> $.getControl().targets)) {
            for (Effect effect : block.getEffects()) {
                try {
                    Ty[] retTys = inferInsn(effect.insn());
                    if (retTys == null) continue;
                    List<Var> assignsTo = effect.getAssignsTo();
                    if (assignsTo.size() != retTys.length) {
                        throw new IllegalStateException(String.format(
                                "insn return type mismatch, assigns %d, inferred %d",
                                assignsTo.size(), retTys.length
                        ));
                    }
                    int i = 0;
                    for (Var var : assignsTo) {
                        var.attachExt(tyExt, retTys[i++]);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(String.format(
                            "error inferring %s; in block: %s",
                            effect,
                            block.toTargetString()
                    ), e);
                }
            }
        }
    }

    protected abstract Ty[] inferInsn(Insn insn);

    interface FuncType<Ty> {
        Ty[] inferResult(Ty[] args);

        int arity();

        static <Ty> FuncType<Ty> withArity(int n, F<Ty[], Ty[]> f) {
            return new FuncType<Ty>() {
                @Override
                public Ty[] inferResult(Ty[] args) {
                    return f.apply(args);
                }

                @Override
                public int arity() {
                    return n;
                }
            };
        }
    }

    static class SFuncType<Ty> implements FuncType<Ty> {
        // null => store for type variable
        // Integer => check type variable
        // Ty => check equality
        final Object[] lhs, rhs;
        final String display;

        SFuncType(Object[] lhs, Object[] rhs, String display) {
            this.lhs = lhs;
            this.rhs = rhs;
            this.display = display;
        }

        @Override
        public int arity() {
            return lhs.length;
        }

        @Override
        public Ty[] inferResult(Ty[] args) {
            if (lhs.length != args.length) {
                throw new IllegalArgumentException("parameter length mismatch");
            }
            @SuppressWarnings("unchecked")
            Ty[] tvs = (Ty[]) new Object[lhs.length];
            for (int i = 0; i < args.length; i++) {
                Object lhsTy = lhs[i];
                Ty argTy = args[i];
                if (lhsTy == null) {
                    tvs[i] = argTy;
                } else {
                    Ty toCheck;
                    if (lhsTy instanceof Integer) {
                        toCheck = tvs[(int) lhsTy];
                    } else {
                        @SuppressWarnings("unchecked")
                        Ty t = (Ty) lhsTy;
                        toCheck = t;
                    }
                    if (argTy != null && !Objects.equals(toCheck, argTy)) {
                        throw new IllegalArgumentException(String.format(
                                "type mismatch: expected %s at index %d, got %s",
                                toCheck, i, argTy
                        ));
                    }
                }
            }
            @SuppressWarnings("unchecked")
            Ty[] rets = (Ty[]) Array.newInstance(args.getClass().getComponentType(), rhs.length);
            for (int i = 0; i < rhs.length; i++) {
                Ty ret;
                Object rhsTy = rhs[i];
                if (rhsTy instanceof Integer) {
                    ret = tvs[(int) rhsTy];
                } else {
                    @SuppressWarnings("unchecked")
                    Ty t = (Ty) rhsTy;
                    ret = t;
                }
                rets[i] = ret;
            }
            return rets;
        }

        @Override
        public String toString() {
            return display;
        }

        public static class Parser<Ty> {
            private final F<String, Ty> parseTy;

            public Parser(F<String, Ty> parseTy) {
                this.parseTy = parseTy;
            }

            public SFuncType<Ty> parse(String str) {
                String[] lhsAndRhs = str.split("->");
                if (lhsAndRhs.length != 2) {
                    throw new IllegalArgumentException(str);
                }
                String[][] lhsAndRhsSplits = new String[2][];
                for (int i = 0; i < lhsAndRhs.length; i++) {
                    String trimmed = lhsAndRhs[i].trim();
                    if (trimmed.isEmpty()) {
                        lhsAndRhsSplits[i] = new String[0];
                    } else {
                        lhsAndRhsSplits[i] = trimmed.split(" ");
                    }
                }

                Map<String, Integer> typeVariables = new HashMap<>();
                Object[][] lhsAndRhsTys = new Object[2][];
                for (int i = 0; i < lhsAndRhsSplits.length; i++) {
                    String[] sideSplit = lhsAndRhsSplits[i];
                    Object[] sideTys = new Object[sideSplit.length];
                    lhsAndRhsTys[i] = sideTys;
                    for (int j = 0; j < sideSplit.length; j++) {
                        String singleTy = sideSplit[j];
                        Object value;
                        if (Character.isLowerCase(singleTy.charAt(0))) {
                            if (typeVariables.containsKey(singleTy)) {
                                value = typeVariables.get(singleTy);
                            } else if (i == 0) {
                                typeVariables.put(singleTy, j);
                                value = null;
                            } else {
                                throw new IllegalArgumentException("new type variables only allowed in lhs");
                            }
                        } else {
                            value = parseTy.apply(singleTy);
                        }
                        sideTys[j] = value;
                    }
                }
                return new SFuncType<>(lhsAndRhsTys[0], lhsAndRhsTys[1], str);
            }
        }
    }

    public static class Java extends InferTypes<Type> {
        public static Java INSTANCE = new Java();

        private Java() {
            super(JavaExts.TYPE);
        }

        private interface Inferer {
            Type[] infer(Insn insn);

            static Inferer fromFuncType(SFuncType<Type> ft) {
                return insn -> ft.inferResult(getArgTys(insn));
            }
        }

        @NotNull
        private static Type[] getArgTys(Insn insn) {
            Type[] argTys = new Type[insn.args.size()];
            int i = 0;
            for (Var arg : insn.args) {
                argTys[i++] = arg.getExt(JavaExts.TYPE).orElse(null);
            }
            return argTys;
        }

        private static class MapBuilder<T, R> {
            private final F<String, R> mapper;
            private final Map<T, R> map = new HashMap<>();

            private MapBuilder(F<String, R> mapper) {
                this.mapper = mapper;
            }

            public Map<T, R> build() {
                return map;
            }

            @SafeVarargs
            public final MapBuilder<T, R> put(R inf, T... ts) {
                for (T t : ts) {
                    map.put(t, inf);
                }
                return this;
            }

            @SafeVarargs
            public final MapBuilder<T, R> put(String str, T... ts) {
                return put(mapper.apply(str), ts);
            }
        }

        private static final Map<Integer, F<AbstractInsnNode, FuncType<Type>>> OPCODE_MAP = new MapBuilder<Integer, F<AbstractInsnNode, FuncType<Type>>>(
                ((F<String, FuncType<Type>>)
                        new SFuncType.Parser<>(Type::getType)::parse)
                        .andThen(it -> $ -> it))
                .put(insn -> {
                            String desc = ((MethodInsnNode) insn).desc;
                            Type[] argTys = Type.getArgumentTypes(desc);
                            Type retTy = Type.getReturnType(desc);
                            Type[] res = retTy.getSize() == 0 ? new Type[0] : new Type[]{retTy};
                            int arity = argTys.length;
                            if (insn.getOpcode() != INVOKESTATIC) {
                                arity += 1;
                            }
                            return withArity(arity, $ -> res);
                        },
                        INVOKESPECIAL, INVOKEVIRTUAL, INVOKEINTERFACE, INVOKESTATIC)
                .put(insn -> {
                            String desc = ((FieldInsnNode) insn).desc;
                            Type[] res = {Type.getType(desc)};
                            return withArity(insn.getOpcode() == GETSTATIC ? 0 : 1, $ -> res);
                        },
                        GETSTATIC, GETFIELD)
                .put(insn ->
                        withArity(0, $ -> new Type[]{Type.getObjectType(((TypeInsnNode) insn).desc)}), NEW)

                .put(" -> ", NOP)
                .put("a -> ", POP)

                .put("I -> J", I2L)
                .put("I -> F", I2F)
                .put("I -> D", I2D)
                .put("F -> I", F2I)
                .put("F -> J", F2L)
                .put("F -> D", F2D)
                .put("D -> J", D2L)
                .put("D -> I", D2I)
                .put("D -> F", D2F)
                .put("J -> D", L2D)
                .put("J -> I", L2I)
                .put("J -> F", L2F)

                .put("I -> I", INEG, I2B, I2C, I2S)
                .put("J -> J", LNEG)
                .put("F -> F", FNEG)
                .put("D -> D", DNEG)

                .put($$ -> withArity(0, $ -> new Type[]{JavaExts.BOTTOM_TYPE}), ACONST_NULL)
                .put(" -> I",
                        ICONST_M1, ICONST_0, ICONST_1, ICONST_2, ICONST_3, ICONST_4, ICONST_5,
                        BIPUSH, SIPUSH)
                .put(" -> J", LCONST_0, LCONST_1)
                .put(" -> F", FCONST_0, FCONST_1, FCONST_2)
                .put(" -> D", DCONST_0, DCONST_1)

                .put($$ -> withArity(2, $ -> new Type[]{$[0].getElementType()}), AALOAD)
                .put("[I I I -> ", IASTORE)
                .put("[Z I I -> ", BASTORE)
                .put("[C I I -> ", CASTORE)
                .put("[S I I -> ", SASTORE)
                .put("[F I I -> ", FASTORE)
                .put("a I b -> ", AASTORE)
                .put("[J I J -> ", LASTORE)
                .put("[D I D -> ", DASTORE)

                .put("[I I -> I", IALOAD)
                .put("[Z I -> I", BALOAD)
                .put("[C I -> I", CALOAD)
                .put("[S I -> I", SALOAD)
                .put("[F I -> I", FALOAD)
                .put("[L I -> L", LALOAD)
                .put("[D I -> D", DALOAD)

                .put("J J -> I", LCMP)
                .put("F F -> I", FCMPG, FCMPL)
                .put("D D -> I", DCMPG, DCMPL)

                .put("I I -> I", IADD, ISUB, IMUL, IDIV, IREM, IAND, IOR, IXOR, ISHL, ISHR, IUSHR)
                .put("J J -> J", LADD, LSUB, LMUL, LDIV, LREM, LAND, LOR, LXOR)
                .put("F F -> F", FADD, FSUB, FMUL, FDIV, FREM)
                .put("D D -> D", DADD, DSUB, DMUL, DDIV, DREM)
                .put("J I -> J", LSHL, LSHR, LUSHR)

                .put("a -> I", ARRAYLENGTH, INSTANCEOF)

                .build();

        private static final Map<OpKey, Inferer> OP_MAP = new MapBuilder<OpKey, Inferer>(
                ((F<String, SFuncType<Type>>) new SFuncType.Parser<>(Type::getType)::parse)
                        .andThen(Inferer::fromFuncType)
        )
                .put(Java::getArgTys, CommonOps.IDENTITY.key)

                .put($ -> {
                            throw new IllegalStateException("please lower intrinsics first");
                        },
                        JavaOps.INTRINSIC)

                .put(insn -> new Type[]{Type.getType(JavaOps.GET_FIELD.cast(insn.op).arg.descriptor)},
                        JavaOps.GET_FIELD)

                .put(insn -> {
                            Type retTy = Type.getReturnType(JavaOps.INVOKE.cast(insn.op).arg.descriptor);
                            return retTy.getSize() == 0 ? new Type[0] : new Type[]{retTy};
                        },
                        JavaOps.INVOKE)

                .put(insn -> new Type[]{insn.args.get(0).getExt(JavaExts.TYPE)
                        .map(Type::getElementType)
                        .orElse(null)},
                        JavaOps.ARRAY_GET)

                .put(insn -> new Type[]{}, JavaOps.PUT_FIELD, JavaOps.ARRAY_SET)

                .put(" -> Ljava/lang/Object;", JavaOps.THIS.key)

                .put((Insn insn) -> {
                    Object cst = CommonOps.CONST.cast(insn.op).arg;
                    Type ty;
                    if (cst == null) ty = JavaExts.BOTTOM_TYPE;
                    else if (cst instanceof Integer) ty = Type.INT_TYPE;
                    else if (cst instanceof Long) ty = Type.LONG_TYPE;
                    else if (cst instanceof Float) ty = Type.FLOAT_TYPE;
                    else if (cst instanceof Double) ty = Type.DOUBLE_TYPE;
                    else ty = Type.getType(cst.getClass());
                    return new Type[]{ty};
                }, CommonOps.CONST)

                .put("I b b -> b", JavaOps.SELECT)
                .put("I -> I", JavaOps.BOOL_SELECT)
                .put((Insn insn) -> {
                    boolean isBot = false;
                    for (Var arg : insn.args) {
                        Type ty = arg.getExt(JavaExts.TYPE).orElse(null);
                        if (ty != null) {
                            if (ty != JavaExts.BOTTOM_TYPE) {
                                return new Type[]{ty};
                            } else {
                                isBot = true;
                            }
                        }
                    }
                    return isBot ? new Type[]{JavaExts.BOTTOM_TYPE} : null;
                }, CommonOps.PHI)
                .put((Insn insn) -> {
                            String desc = insn.getExtOrThrow(CommonExts.OWNING_EFFECT)
                                    .getExtOrThrow(CommonExts.OWNING_BLOCK)
                                    .getExtOrThrow(CommonExts.OWNING_FUNCTION)
                                    .getExtOrThrow(JavaExts.FUNCTION_DESCRIPTOR);
                            int arg = CommonOps.ARG.cast(insn.op).arg;
                            return new Type[]{Type.getArgumentTypes(desc)[arg]};
                        },
                        CommonOps.ARG)
                .put((Insn insn) -> {
                            List<Type> stack = new ArrayList<>(Arrays.asList(getArgTys(insn)));
                            for (AbstractInsnNode insnN : JavaOps.INSNS.cast(insn.op).arg) {
                                int opc = insnN.getOpcode();
                                if (!OPCODE_MAP.containsKey(opc)) {
                                    throw new IllegalStateException("unsupported opcode " + opc);
                                }
                                FuncType<Type> inf = OPCODE_MAP.get(opc).apply(insnN);
                                Type[] argTys = new Type[inf.arity()];
                                for (int i = inf.arity() - 1; i >= 0; i--) {
                                    argTys[i] = stack.remove(stack.size() - 1);
                                }
                                stack.addAll(Arrays.asList(inf.inferResult(argTys)));
                            }
                            return stack.toArray(new Type[0]);
                        },
                        JavaOps.INSNS
                )
                .build();

        @Override
        protected Type[] inferInsn(Insn insn) {
            Inferer inf = OP_MAP.get(insn.op.key);
            if (inf == null) {
                throw new IllegalStateException("unsupported op: " + insn.op.key);
            }
            return inf.infer(insn);
        }
    }
}

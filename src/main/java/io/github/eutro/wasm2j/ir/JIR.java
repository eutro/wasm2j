package io.github.eutro.wasm2j.ir;

import io.github.eutro.jwasm.tree.CodeNode;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.ir.JIR.SelectExpr.JumpType;
import io.github.eutro.wasm2j.ir.SSA.AssignmentDest.VarDest;
import io.github.eutro.wasm2j.ir.SSA.Effect.AssignmentStmt;
import io.github.eutro.wasm2j.ir.SSA.Expr.ConstExpr;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.stream.Collectors;

import static io.github.eutro.jwasm.Opcodes.*;
import static io.github.eutro.wasm2j.ir.JIR.SelectExpr.boolSelect;

public class JIR extends SSA {
    public static class Class {
        public final List<Annotation> annotations = new ArrayList<>();
        public final List<Field> fields = new ArrayList<>();
        public final List<Method> methods = new ArrayList<>();
    }

    public static class Annotation {
        public Type type;
        public Map<String, Object> args = new HashMap<>();
    }

    public static class Field {
        public final List<Annotation> annotations = new ArrayList<>();
        public String name;
        public Type type;

        public Field(String name, Type type) {
            this.name = name;
            this.type = type;
        }
    }

    public static class Method {
        public final List<Annotation> annotations = new ArrayList<>();
        public String name;
        public List<Type> paramTypes = new ArrayList<>();
        public Type returnType;

        public Method(String name, Type returnType) {
            this.name = name;
            this.returnType = returnType;
        }

        public final Function func = new Function();
    }

    public static class Br extends Control {
        public BasicBlock target;

        public Br(BasicBlock target) {
            this.target = target;
        }

        @Override
        public Iterable<Expr> exprs() {
            return Collections.emptyList();
        }

        @Override
        public Iterable<BasicBlock> targets() {
            return Collections.singletonList(target);
        }

        @Override
        public String toString() {
            return "br " + target.toTargetString();
        }
    }

    public static class BrCond extends Control {
        public JumpType type;
        public Expr[] args;
        public BasicBlock ifT, ifF;

        public BrCond(JumpType type, Expr[] args, BasicBlock ifT, BasicBlock ifF) {
            this.type = type;
            this.args = args;
            this.ifT = ifT;
            this.ifF = ifF;
        }

        @Override
        public Iterable<Expr> exprs() {
            return Arrays.asList(args);
        }

        @Override
        public Iterable<BasicBlock> targets() {
            return Arrays.asList(ifT, ifF);
        }

        @Override
        public String toString() {
            return type.toString().toLowerCase(Locale.ROOT)
                    + Arrays.toString(args)
                    + " " + ifT.toTargetString()
                    + " " + ifF.toTargetString();
        }
    }

    public static class SelectExpr extends Expr {
        // this is easier to optimise than fat blocks and phis
        public final Expr[] cond;
        public final Expr ifT, ifF;
        public final JumpType type;

        public enum JumpType {
            IFNE(Opcodes.IFNE),
            IFEQ(Opcodes.IFEQ),
            IFLT(Opcodes.IFLT),
            IFGE(Opcodes.IFGE),
            IFGT(Opcodes.IFGT),
            IFLE(Opcodes.IFLE),
            IFNULL(Opcodes.IFNULL),
            IF_ICMPEQ(Opcodes.IF_ICMPEQ),
            IF_ICMPNE(Opcodes.IF_ICMPNE),
            IF_ICMPLT(Opcodes.IF_ICMPLT),
            IF_ICMPGE(Opcodes.IF_ICMPGE),
            IF_ICMPGT(Opcodes.IF_ICMPGT),
            IF_ICMPLE(Opcodes.IF_ICMPLE),
            IF_ACMPEQ(Opcodes.IF_ACMPEQ),
            IF_ACMPNE(Opcodes.IF_ACMPNE),
            ;

            public final int opcode;

            JumpType(int opcode) {
                this.opcode = opcode;
            }
        }

        public SelectExpr(Expr cond, Expr ifT, Expr ifF, JumpType type) {
            this(new Expr[]{cond}, ifT, ifF, type);
        }

        public SelectExpr(Expr[] cond, Expr ifT, Expr ifF, JumpType type) {
            this.cond = cond;
            this.ifT = ifT;
            this.ifF = ifF;
            this.type = type;
        }

        public static SelectExpr boolSelect(JumpType type, Expr... cond) {
            return new SelectExpr(cond, new ConstExpr(1), new ConstExpr(0), type);
        }

        @Override
        public Iterable<Expr> children() {
            ArrayList<Expr> exprs = new ArrayList<>(Arrays.asList(cond));
            exprs.addAll(Arrays.asList(ifT, ifF));
            return exprs;
        }

        @Override
        public String toString() {
            return type.toString().toLowerCase(Locale.ROOT) + " " + Arrays.toString(cond) + " ? " + ifT + " : " + ifF;
        }
    }

    public static class InsnListExpr extends Expr {
        public final InsnList insns;
        public final Expr[] args;

        public InsnListExpr(InsnList insns, Expr[] args) {
            this.insns = insns;
            this.args = args;
        }

        @Override
        public Iterable<Expr> children() {
            return Arrays.asList(args);
        }

        private Map<Integer, String> opcodeMnemonics = null;

        @Nullable
        private String disassInsn(AbstractInsnNode insn) {
            if (opcodeMnemonics == null) {
                opcodeMnemonics = new HashMap<>();
                try {
                    java.lang.reflect.Field[] fields = Opcodes.class.getFields();
                    for (java.lang.reflect.Field field : fields) {
                        if (field.getType() == int.class) {
                            opcodeMnemonics.put((Integer) field.get(null), field.getName());
                        }
                    }
                } catch (ReflectiveOperationException ignored) {
                }
            }
            return opcodeMnemonics.get(insn.getOpcode());
        }

        @Override
        public String toString() {
            String name = "{{opaque}}";
            if (insns.size() == 1) {
                AbstractInsnNode insn = insns.getFirst();
                String insnName = disassInsn(insn);
                if (insnName != null) name = insnName;
            }
            return name + Arrays.stream(args)
                    .map(Objects::toString)
                    .collect(Collectors.joining(", ", "(", ")"));
        }
    }

    public static class ThisExpr extends Expr {
        @Override
        public Iterable<Expr> children() {
            return Collections.emptyList();
        }

        @Override
        public String toString() {
            return "this";
        }
    }

    public static class FieldRefExpr extends Expr {
        public Expr target;
        public Field field;

        public FieldRefExpr(Expr target, Field field) {
            this.target = target;
            this.field = field;
        }

        @Override
        public Iterable<Expr> children() {
            return Collections.singletonList(target);
        }

        @Override
        public String toString() {
            return target + "->" + field.name;
        }
    }

    public static class MethodCallExpr extends Expr {
        public Expr target;
        public Method method;
        public Expr[] args;

        public MethodCallExpr(Expr target, Method method, Expr[] args) {
            this.target = target;
            this.method = method;
            this.args = args;
        }

        @Override
        public Iterable<Expr> children() {
            return Collections.emptyList();
        }

        @Override
        public String toString() {
            return target + "->" + method.name +
                    Arrays.stream(args)
                            .map(Objects::toString)
                            .collect(Collectors.joining(", ", "(", ")"));
        }
    }

    public static class MemoryExpr extends Expr {
        public Expr memory, address;
        public MemoryType type;

        public enum MemoryType {
            BYTE("get", "put"),
            SHORT("getShort", "putShort"),
            INT("getInt", "putInt"),
            LONG("getLong", "putLong"),
            FLOAT("getFloat", "putFloat"),
            DOUBLE("getDouble", "putDouble"),
            ;

            public final String get;
            public final String put;

            MemoryType(
                    @Language(
                            value = "JAVA",
                            prefix = "class X{{ByteBuffer.wrap(new byte[0]).",
                            suffix = "(0);}}"
                    )
                    String get,
                    @Language(
                            value = "JAVA",
                            prefix = "class X{{ByteBuffer.wrap(new byte[0]).",
                            suffix = "(0, (byte) 0);}}"
                    )
                    String put
            ) {
                this.get = get;
                this.put = put;
            }
        }

        public MemoryExpr(Expr memory, Expr address, MemoryType type) {
            this.memory = memory;
            this.address = address;
            this.type = type;
        }

        @Override
        public Iterable<Expr> children() {
            return Arrays.asList(memory, address);
        }

        @Override
        public String toString() {
            return "(" + type.toString().toLowerCase(Locale.ROOT) + ")" + memory + "[" + address + "]";
        }
    }

    public static class ArrayExpr extends Expr {
        public final Expr table, index;

        public ArrayExpr(Expr table, Expr index) {
            this.table = table;
            this.index = index;
        }

        @Override
        public Iterable<Expr> children() {
            return Arrays.asList(table, index);
        }

        @Override
        public String toString() {
            return table + "[" + index + "]";
        }
    }

    public static class ArrayDest extends AssignmentDest {
        public final Expr table, index;

        public ArrayDest(Expr table, Expr index) {
            this.table = table;
            this.index = index;
        }

        @Override
        public Collection<Expr> exprs() {
            return Arrays.asList(table, index);
        }

        @Override
        public String toString() {
            return table + "[" + index + "]";
        }
    }

    public static class MemoryDest extends AssignmentDest {
        public Expr memory, address;
        public MemoryExpr.MemoryType type;

        public MemoryDest(Expr memory, Expr address, MemoryExpr.MemoryType type) {
            this.memory = memory;
            this.address = address;
            this.type = type;
        }

        @Override
        public Collection<Expr> exprs() {
            return Arrays.asList(memory, address);
        }

        @Override
        public String toString() {
            return "*(" + type.toString().toLowerCase(Locale.ROOT) + " *)&" + memory + "[" + address + "]";
        }
    }

    public static class FieldRefDest extends AssignmentDest {
        public Expr target;
        public Field field;

        public FieldRefDest(Expr target, Field field) {
            this.target = target;
            this.field = field;
        }

        @Override
        public Collection<Expr> exprs() {
            return Collections.singletonList(target);
        }

        @Override
        public String toString() {
            return field + "." + field.name;
        }
    }

    public static class JIRBuilder {
        public final Function func;
        public BasicBlock bb;
        public Map<BasicBlock, BasicBlock> bbMap = new HashMap<>();
        private FullConvertContext ctx;

        public JIRBuilder(FullConvertContext ctx, Function function) {
            this.ctx = ctx;
            this.func = function;
        }

        public Field global(int i) {
            return ctx.globals.get(i);
        }

        public Field mem(int i) {
            return ctx.mems.get(i);
        }

        public Field table(int i) {
            return ctx.tables.get(i);
        }

        public Method func(int i) {
            return ctx.funcs.get(i);
        }

        public BasicBlock mapBb(BasicBlock old) {
            BasicBlock bb = bbMap.get(old);
            if (bb == null) {
                throw new IllegalArgumentException();
            }
            return bb;
        }

        public void setBb(BasicBlock bb) {
            this.bb = bb;
        }

        public void buildSideEffect(Expr expr) {
            bb.effects.add(new Effect.SideEffectStmt(expr));
        }

        public Expr buildInsn(String name, Expr expr) {
            Var theVar = func.newVar(name);
            bb.effects.add(new AssignmentStmt(expr, new VarDest(theVar)));
            return new Expr.VarExpr(theVar);
        }

        public Expr buildSInsn(String name, AbstractInsnNode insn, Expr... args) {
            InsnList insnList = new InsnList();
            insnList.add(insn);
            return buildInsn(name, new InsnListExpr(insnList, args));
        }

        public Expr buildSInsn(String name, int opcode, Expr... args) {
            return buildSInsn(name, new InsnNode(opcode), args);
        }

        public Expr loadField(Field field) {
            return new FieldRefExpr(buildInsn("this", new ThisExpr()), field);
        }

        public void buildJump(Control ctrl) {
            bb.control = ctrl;
        }
    }

    private interface WIRConvertHandler<T, R> {
        R convert(JIRBuilder jb, T t);

        @SuppressWarnings("unchecked")
        default <O> O convertUnchecked(JIRBuilder jb, Object obj) {
            return (O) convert(jb, (T) obj);
        }
    }

    private static final Map<java.lang.Class<?>, WIRConvertHandler<?, ?>> CONVERTERS = new HashMap<>();

    private static <T extends Control> void registerCtrlConverter(java.lang.Class<T> theClass, WIRConvertHandler<T, Control> handler) {
        CONVERTERS.put(theClass, handler);
    }

    private static <T extends Effect> void registerStmtConverter(java.lang.Class<T> theClass, WIRConvertHandler<T, Void> handler) {
        CONVERTERS.put(theClass, handler);
    }

    private static <T extends AssignmentDest> void registerDestConverter(java.lang.Class<T> theClass, WIRConvertHandler<T, AssignmentDest> handler) {
        CONVERTERS.put(theClass, handler);
    }

    private static <T extends Expr> void registerExprConverter(java.lang.Class<T> theClass, WIRConvertHandler<T, Expr> handler) {
        CONVERTERS.put(theClass, handler);
    }

    // effects
    static {
        registerStmtConverter(Effect.SideEffectStmt.class, (jb, stmt) -> {
            jb.buildSideEffect(convertExpr(jb, stmt.value));
            return null;
        });
        registerStmtConverter(Effect.AssignmentStmt.class, (jb, stmt) -> {
            jb.bb.effects.add(new AssignmentStmt(
                    convertExpr(jb, stmt.value),
                    convertDest(jb, stmt.dest)
            ));
            return null;
        });
        registerStmtConverter(Effect.AssignManyStmt.class, (jb, stmt) -> {
            if (stmt.dest.length == 0) {
                jb.buildSideEffect(convertExpr(jb, stmt.value));
            } else {
                throw new UnsupportedOperationException("Multiple returns not supported");
            }
            return null;
        });
    }

    // controls
    static {
        registerCtrlConverter(Control.Return.class, (jb, stmt) -> {
            Expr[] values = new Expr[stmt.values.length];
            for (int i = 0; i < values.length; i++) {
                values[i] = convertExpr(jb, stmt.values[i]);
            }
            return new Control.Return(values);
        });
        registerCtrlConverter(Control.Break.class, (jb, stmt) -> {
            if (stmt.targets.length == 0) {
                return new Br(jb.mapBb(stmt.dfltTarget));
            } else if (stmt.targets.length == 1) {
                return new BrCond(
                        JumpType.IFEQ,
                        new Expr[]{convertExpr(jb, stmt.cond)},
                        jb.mapBb(stmt.targets[0]),
                        jb.mapBb(stmt.dfltTarget)
                );
            } else {
                BasicBlock[] targets = new BasicBlock[stmt.targets.length];
                for (int i = 0; i < targets.length; i++) {
                    targets[i] = jb.mapBb(targets[i]);
                }
                return new Control.Break(targets, jb.mapBb(stmt.dfltTarget), convertExpr(jb, stmt.cond));
            }
        });
        registerCtrlConverter(Control.Unreachable.class, (jb, stmt) -> {
            InsnList insns = new InsnList();
            insns.add(new TypeInsnNode(Opcodes.NEW, Type.getInternalName(RuntimeException.class)));
            insns.add(new InsnNode(Opcodes.DUP));
            insns.add(new MethodInsnNode(
                    Opcodes.INVOKESPECIAL,
                    "<init>",
                    Type.getInternalName(RuntimeException.class),
                    "(Ljava/lang/String;)V",
                    false
            ));
            insns.add(new InsnNode(Opcodes.ATHROW));
            jb.buildSideEffect(new InsnListExpr(insns, new Expr[0]));
            return stmt;
        });
    }

    // dests
    static {
        registerDestConverter(VarDest.class, (jb, dest) -> dest);
        // TODO
        registerDestConverter(WIR.GlobalDest.class, (jb, dest) ->
                new FieldRefDest(jb.buildInsn("this", new ThisExpr()), jb.global(dest.var)));
        registerDestConverter(WIR.MemoryDest.class, (jb, dest) ->
                new MemoryDest(jb.buildInsn("mem", jb.loadField(jb.mem(0))),
                        convertExpr(jb, dest.address),
                        MemoryExpr.MemoryType.BYTE // FIXME
                ));
        registerDestConverter(WIR.TableDest.class, (jb, dest) ->
                new ArrayDest(jb.buildInsn("table", jb.loadField(jb.table(0))),
                        convertExpr(jb, dest.index)));
    }

    // region exprs
    static {
        for (java.lang.Class<? extends Expr> cls : Arrays.asList(
                Expr.PhiExpr.class,
                Expr.VarExpr.class,
                Expr.FuncArgExpr.class,
                ConstExpr.class
        )) {
            registerExprConverter(cls, (jb, expr) -> expr);
        }

        registerExprConverter(WIR.NullExpr.class, (jb, expr) -> new ConstExpr(null));
        registerExprConverter(WIR.SelectExpr.class, (jb, expr) -> new SelectExpr(expr.cond, expr.ifT, expr.ifF, JumpType.IFNE));
        registerExprConverter(WIR.ZeroInitExpr.class, (jb, expr) -> {
            switch (expr.type) {
                case I32:
                    return new ConstExpr(0);
                case I64:
                    return new ConstExpr(0L);
                case F32:
                    return new ConstExpr(0F);
                case F64:
                    return new ConstExpr(0D);
                case FUNCREF:
                case EXTERNREF:
                    return new ConstExpr(null);
                default:
                    throw new IllegalArgumentException();
            }
        });
        registerExprConverter(WIR.IsNullExpr.class, (jb, expr) -> boolSelect(JumpType.IFNULL, expr.value));
        registerExprConverter(WIR.DerefExpr.class, (jb, expr) -> new MemoryExpr(
                jb.loadField(jb.mem(0)),
                convertExpr(jb, expr.address),
                MemoryExpr.MemoryType.BYTE // FIXME
        ));
    }

    @SuppressWarnings("unchecked")
    private static final WIRConvertHandler<WIR.OperatorExpr, Expr>[] OP_CONVERTERS
            = (WIRConvertHandler<WIR.OperatorExpr, Expr>[]) new WIRConvertHandler[Byte.toUnsignedInt((byte) -1)];
    private static final Map<Integer, WIRConvertHandler<WIR.OperatorExpr, Expr>> INT_OP_CONVERTERS = new HashMap<>();

    static {
        registerExprConverter(WIR.OperatorExpr.class, (jb, expr) -> {
            WIRConvertHandler<WIR.OperatorExpr, Expr> converter = OP_CONVERTERS[Byte.toUnsignedInt(expr.op)];
            if (converter != null) {
                return converter.convert(jb, expr);
            }
            if (expr.op == INSN_PREFIX) {
                converter = INT_OP_CONVERTERS.get(expr.intOp);
            }
            if (converter != null) {
                return converter.convert(jb, expr);
            }
            throw new UnsupportedOperationException("Operator not supported");
        });
    }

    private static void opConv(byte op, WIRConvertHandler<WIR.OperatorExpr, Expr> converter) {
        OP_CONVERTERS[Byte.toUnsignedInt(op)] = converter;
    }

    private static void iopConv(int op, WIRConvertHandler<WIR.OperatorExpr, Expr> converter) {
        INT_OP_CONVERTERS.put(op, converter);
    }

    // region comparisons
    private static Expr intCmpU(JIRBuilder jb, WIR.OperatorExpr expr) {
        return jb.buildSInsn(
                "cmp",
                new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(Integer.class),
                        "compareUnsigned",
                        "(II)I"
                ),
                expr.args
        );
    }

    private static Expr longCmp(JIRBuilder jb, WIR.OperatorExpr expr) {
        return longCmp(jb, expr.args);
    }

    private static Expr longCmp(JIRBuilder jb, Expr... args) {
        return jb.buildSInsn("cmp", Opcodes.LCMP, args);
    }

    private static Expr longCmpU(JIRBuilder jb, WIR.OperatorExpr expr) {
        return jb.buildSInsn(
                "cmp",
                new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(Long.class),
                        "compareUnsigned",
                        "(JJ)I"
                ),
                expr.args
        );
    }

    private static Expr fCmpG(JIRBuilder jb, WIR.OperatorExpr expr) {
        return jb.buildSInsn("cmp", Opcodes.FCMPG, expr.args);
    }

    private static Expr fCmpL(JIRBuilder jb, WIR.OperatorExpr expr) {
        return jb.buildSInsn("cmp", Opcodes.FCMPL, expr.args);
    }

    private static Expr dCmpG(JIRBuilder jb, WIR.OperatorExpr expr) {
        return jb.buildSInsn("cmp", Opcodes.DCMPG, expr.args);
    }

    private static Expr dCmpL(JIRBuilder jb, WIR.OperatorExpr expr) {
        return jb.buildSInsn("cmp", Opcodes.DCMPL, expr.args);
    }

    static {
        opConv(I32_EQZ, (jb, expr) -> boolSelect(JumpType.IFEQ, expr.args));
        opConv(I32_EQ, (jb, expr) -> boolSelect(JumpType.IF_ICMPEQ, expr.args));
        opConv(I32_NE, (jb, expr) -> boolSelect(JumpType.IF_ICMPNE, expr.args));
        opConv(I32_LT_S, (jb, expr) -> boolSelect(JumpType.IF_ICMPLT, expr.args));
        opConv(I32_LT_U, (jb, expr) -> boolSelect(JumpType.IFLT, intCmpU(jb, expr)));
        opConv(I32_GT_S, (jb, expr) -> boolSelect(JumpType.IF_ICMPGT, expr.args));
        opConv(I32_GT_U, (jb, expr) -> boolSelect(JumpType.IFGT, intCmpU(jb, expr)));
        opConv(I32_LE_S, (jb, expr) -> boolSelect(JumpType.IF_ICMPLE, expr.args));
        opConv(I32_LE_U, (jb, expr) -> boolSelect(JumpType.IFLE, intCmpU(jb, expr)));
        opConv(I32_GE_S, (jb, expr) -> boolSelect(JumpType.IF_ICMPGE, expr.args));
        opConv(I32_GE_U, (jb, expr) -> boolSelect(JumpType.IFGE, intCmpU(jb, expr)));

        opConv(I64_EQZ, (jb, expr) -> boolSelect(JumpType.IFEQ, longCmp(jb, expr.args[0], new ConstExpr(0))));
        opConv(I64_EQ, (jb, expr) -> boolSelect(JumpType.IFEQ, longCmp(jb, expr)));
        opConv(I64_NE, (jb, expr) -> boolSelect(JumpType.IFNE, longCmp(jb, expr)));
        opConv(I64_LT_S, (jb, expr) -> boolSelect(JumpType.IFLT, longCmp(jb, expr)));
        opConv(I64_LT_U, (jb, expr) -> boolSelect(JumpType.IFLT, longCmpU(jb, expr)));
        opConv(I64_GT_S, (jb, expr) -> boolSelect(JumpType.IFGT, longCmp(jb, expr)));
        opConv(I64_GT_U, (jb, expr) -> boolSelect(JumpType.IFGT, longCmpU(jb, expr)));
        opConv(I64_LE_S, (jb, expr) -> boolSelect(JumpType.IFLE, longCmp(jb, expr)));
        opConv(I64_LE_U, (jb, expr) -> boolSelect(JumpType.IFLE, longCmpU(jb, expr)));
        opConv(I64_GE_S, (jb, expr) -> boolSelect(JumpType.IFGE, longCmp(jb, expr)));
        opConv(I64_GE_U, (jb, expr) -> boolSelect(JumpType.IFGE, longCmpU(jb, expr)));

        opConv(F32_EQ, (jb, expr) -> boolSelect(JumpType.IFEQ, fCmpG(jb, expr)));
        opConv(F32_NE, (jb, expr) -> boolSelect(JumpType.IFNE, fCmpG(jb, expr)));
        opConv(F32_LT, (jb, expr) -> boolSelect(JumpType.IFLT, fCmpG(jb, expr)));
        opConv(F32_GT, (jb, expr) -> boolSelect(JumpType.IFGT, fCmpL(jb, expr)));
        opConv(F32_LE, (jb, expr) -> boolSelect(JumpType.IFLE, fCmpG(jb, expr)));
        opConv(F32_GE, (jb, expr) -> boolSelect(JumpType.IFGE, fCmpL(jb, expr)));

        opConv(F64_EQ, (jb, expr) -> boolSelect(JumpType.IFEQ, dCmpG(jb, expr)));
        opConv(F64_NE, (jb, expr) -> boolSelect(JumpType.IFNE, dCmpG(jb, expr)));
        opConv(F64_LT, (jb, expr) -> boolSelect(JumpType.IFLT, dCmpG(jb, expr)));
        opConv(F64_GT, (jb, expr) -> boolSelect(JumpType.IFGT, dCmpL(jb, expr)));
        opConv(F64_LE, (jb, expr) -> boolSelect(JumpType.IFLE, dCmpG(jb, expr)));
        opConv(F64_GE, (jb, expr) -> boolSelect(JumpType.IFGE, dCmpL(jb, expr)));
    }
    // endregion comparisons

    private static Expr sExpr(AbstractInsnNode insn, Expr... args) {
        InsnList insns = new InsnList();
        insns.add(insn);
        return new InsnListExpr(insns, args);
    }

    private static Expr sExpr(int opcode, Expr... args) {
        return sExpr(new InsnNode(opcode), args);
    }

    private static Expr intFn(
            @Language(
                    value = "JAVA",
                    prefix = "class X{{Integer.",
                    suffix = "()}}"
            ) String name,
            String desc,
            Expr... args
    ) {
        return sExpr(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(Integer.class),
                        name,
                        desc
                ),
                args);
    }

    private static Expr longFn(
            @Language(
                    value = "JAVA",
                    prefix = "class X{{Long.",
                    suffix = "()}}"
            ) String name,
            String desc,
            Expr... args
    ) {
        return sExpr(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(Long.class),
                        name,
                        desc
                ),
                args);
    }

    // region mathematical
    static {
        opConv(I32_CLZ, (jb, expr) -> intFn("numberOfLeadingZeros", "(I)I", expr.args));
        opConv(I32_CTZ, (jb, expr) -> intFn("numberOfTrailingZeros", "(I)I", expr.args));
        opConv(I32_POPCNT, (jb, expr) -> intFn("bitCount", "(I)I", expr.args));
        opConv(I32_ADD, (jb, expr) -> sExpr(Opcodes.IADD, expr.args));
        opConv(I32_SUB, (jb, expr) -> sExpr(Opcodes.ISUB, expr.args));
        opConv(I32_MUL, (jb, expr) -> sExpr(Opcodes.IMUL, expr.args));
        opConv(I32_DIV_S, (jb, expr) -> sExpr(Opcodes.IDIV, expr.args));
        opConv(I32_DIV_U, (jb, expr) -> intFn("divideUnsigned", "(II)I", expr.args));
        opConv(I32_REM_S, (jb, expr) -> sExpr(Opcodes.IREM, expr.args));
        opConv(I32_REM_U, (jb, expr) -> intFn("remainderUnsigned", "(II)I", expr.args));
        opConv(I32_AND, (jb, expr) -> sExpr(Opcodes.IAND, expr.args));
        opConv(I32_OR, (jb, expr) -> sExpr(Opcodes.IOR, expr.args));
        opConv(I32_XOR, (jb, expr) -> sExpr(Opcodes.IXOR, expr.args));
        opConv(I32_SHL, (jb, expr) -> sExpr(Opcodes.ISHL, expr.args));
        opConv(I32_SHR_S, (jb, expr) -> sExpr(Opcodes.ISHR, expr.args));
        opConv(I32_SHR_U, (jb, expr) -> sExpr(Opcodes.IUSHR, expr.args));
        opConv(I32_ROTL, (jb, expr) -> intFn("rotateLeft", "(II)I", expr.args));
        opConv(I32_ROTR, (jb, expr) -> intFn("rotateRight", "(II)I", expr.args));
    }

    private static Expr i2l(JIRBuilder jb, Expr expr) {
        return sExpr(Opcodes.I2L, jb.buildInsn("int", expr));
    }

    private static Expr l2i(JIRBuilder jb, Expr expr) {
        return sExpr(Opcodes.L2I, jb.buildInsn("long", expr));
    }

    private static Expr i2lU(JIRBuilder jb, WIR.OperatorExpr expr) {
        return jb.buildInsn("long", intFn("toUnsignedLong", "(I)J", expr.args));
    }

    static {
        opConv(I64_CLZ, (jb, expr) -> i2l(jb, longFn("numberOfLeadingZeros", "(J)I", expr.args)));
        opConv(I64_CTZ, (jb, expr) -> i2l(jb, longFn("numberOfTrailingZeros", "(J)I", expr.args)));
        opConv(I64_POPCNT, (jb, expr) -> i2l(jb, longFn("bitCount", "(J)I", expr.args)));
        opConv(I64_ADD, (jb, expr) -> sExpr(Opcodes.LADD, expr.args));
        opConv(I64_SUB, (jb, expr) -> sExpr(Opcodes.LSUB, expr.args));
        opConv(I64_MUL, (jb, expr) -> sExpr(Opcodes.LMUL, expr.args));
        opConv(I64_DIV_S, (jb, expr) -> sExpr(Opcodes.LDIV, expr.args));
        opConv(I64_DIV_U, (jb, expr) -> longFn("divideUnsigned", "(JJ)J", expr.args));
        opConv(I64_REM_S, (jb, expr) -> sExpr(Opcodes.LREM, expr.args));
        opConv(I64_REM_U, (jb, expr) -> longFn("remainderUnsigned", "(JJ)J", expr.args));
        opConv(I64_AND, (jb, expr) -> sExpr(Opcodes.LAND, expr.args));
        opConv(I64_OR, (jb, expr) -> sExpr(Opcodes.LOR, expr.args));
        opConv(I64_XOR, (jb, expr) -> sExpr(Opcodes.LXOR, expr.args));
        opConv(I64_SHL, (jb, expr) -> sExpr(Opcodes.LSHL, expr.args));
        opConv(I64_SHR_S, (jb, expr) -> sExpr(Opcodes.LSHR, expr.args));
        opConv(I64_SHR_U, (jb, expr) -> sExpr(Opcodes.LUSHR, expr.args));
        opConv(I64_ROTL, (jb, expr) -> longFn("rotateLeft", "(JI)J", expr.args[0], l2i(jb, expr.args[1])));
        opConv(I64_ROTR, (jb, expr) -> longFn("rotateRight", "(JI)J", expr.args[0], l2i(jb, expr.args[1])));
    }

    private static Expr mathFn(
            @Language(
                    value = "JAVA",
                    prefix = "class X{{Math.",
                    suffix = "()}}"
            ) String name,
            String desc,
            Expr... args
    ) {
        return sExpr(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(Float.class),
                        name,
                        desc
                ),
                args);
    }

    private static Expr floatFn(
            @Language(
                    value = "JAVA",
                    prefix = "class X{{Float.",
                    suffix = "()}}"
            ) String name,
            String desc,
            Expr... args
    ) {
        return sExpr(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(Float.class),
                        name,
                        desc
                ),
                args);
    }

    private static Expr doubleFn(
            @Language(
                    value = "JAVA",
                    prefix = "class X{{Double.",
                    suffix = "()}}"
            ) String name,
            String desc,
            Expr... args
    ) {
        return sExpr(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(Double.class),
                        name,
                        desc
                ),
                args);
    }

    private static Expr f2d(JIRBuilder jb, Expr expr) {
        return sExpr(Opcodes.F2D, jb.buildInsn("float", expr));
    }

    private static Expr d2f(JIRBuilder jb, Expr expr) {
        return sExpr(Opcodes.D2F, jb.buildInsn("double", expr));
    }

    private static WIRConvertHandler<WIR.OperatorExpr, Expr> asFloatHandler(WIRConvertHandler<Expr, Expr> f) {
        return (jb, expr) -> d2f(jb, f.convert(jb, f2d(jb, expr.args[0])));
    }

    static {
        opConv(F32_ABS, (jb, expr) -> mathFn("abs", "(F)F", expr.args));
        opConv(F32_NEG, (jb, expr) -> sExpr(Opcodes.FNEG, expr.args));
        opConv(F32_CEIL, asFloatHandler((jb, expr) -> mathFn("ceil", "(D)D", expr)));
        opConv(F32_FLOOR, asFloatHandler((jb, expr) -> mathFn("floor", "(D)D", expr)));
        // TODO F32_TRUNC, F32_NEAREST
        opConv(F32_SQRT, asFloatHandler((jb, expr) -> mathFn("sqrt", "(D)D", expr)));
        opConv(F32_ADD, (jb, expr) -> sExpr(Opcodes.FADD, expr.args));
        opConv(F32_SUB, (jb, expr) -> sExpr(Opcodes.FSUB, expr.args));
        opConv(F32_MUL, (jb, expr) -> sExpr(Opcodes.FMUL, expr.args));
        opConv(F32_DIV, (jb, expr) -> sExpr(Opcodes.FDIV, expr.args));
        opConv(F32_MIN, (jb, expr) -> mathFn("min", "(FF)F", expr.args));
        opConv(F32_MAX, (jb, expr) -> mathFn("max", "(FF)F", expr.args));
        opConv(F32_COPYSIGN, (jb, expr) -> mathFn("copySign", "(FF)F", expr.args));
    }

    static {
        opConv(F64_ABS, (jb, expr) -> mathFn("abs", "(D)D", expr.args));
        opConv(F64_NEG, (jb, expr) -> sExpr(Opcodes.DNEG, expr.args));
        opConv(F64_CEIL, (jb, expr) -> mathFn("ceil", "(D)D", expr.args));
        opConv(F64_FLOOR, (jb, expr) -> mathFn("floor", "(D)D", expr.args));
        // TODO F64_TRUNC, F64_NEAREST
        opConv(F64_SQRT, (jb, expr) -> mathFn("sqrt", "(D)D", expr.args));
        opConv(F64_ADD, (jb, expr) -> sExpr(Opcodes.DADD, expr.args));
        opConv(F64_SUB, (jb, expr) -> sExpr(Opcodes.DSUB, expr.args));
        opConv(F64_MUL, (jb, expr) -> sExpr(Opcodes.DMUL, expr.args));
        opConv(F64_DIV, (jb, expr) -> sExpr(Opcodes.DDIV, expr.args));
        opConv(F64_MIN, (jb, expr) -> mathFn("min", "(DD)D", expr.args));
        opConv(F64_MAX, (jb, expr) -> mathFn("max", "(DD)D", expr.args));
        opConv(F64_COPYSIGN, (jb, expr) -> mathFn("copySign", "(DD)D", expr.args));
    }
    // endregion mathematical

    // region conversions
    static {
        opConv(I32_WRAP_I64, (jb, expr) -> sExpr(Opcodes.L2I, expr.args));
        // raw truncations are undefined for values out of bounds (i.e. they must trap),
        // this is unimplemented
        // TODO I32_TRUNC_F32_S, I32_TRUNC_F32_U, I32_TRUNC_F64_S, I32_TRUNC_F64_U

        opConv(I64_EXTEND_I32_S, (jb, expr) -> sExpr(Opcodes.I2L, expr.args));
        opConv(I64_EXTEND_I32_U, (jb, expr) -> intFn("toUnsignedLong", "(I)J", expr.args));
        // TODO I64_TRUNC_F32_S, I64_TRUNC_F32_U, I64_TRUNC_F64_S, I64_TRUNC_F64_U

        opConv(F32_CONVERT_I32_S, (jb, expr) -> sExpr(Opcodes.I2F, expr.args));
        opConv(F32_CONVERT_I32_U, (jb, expr) -> sExpr(Opcodes.L2F, i2lU(jb, expr)));
        opConv(F32_CONVERT_I64_S, (jb, expr) -> sExpr(Opcodes.L2F, expr.args));
        // TODO F32_CONVERT_I64_U

        opConv(F32_DEMOTE_F64, (jb, expr) -> sExpr(Opcodes.D2F, expr.args));

        opConv(F64_CONVERT_I32_S, (jb, expr) -> sExpr(Opcodes.I2D, expr.args));
        opConv(F64_CONVERT_I32_U, (jb, expr) -> sExpr(Opcodes.L2D, i2lU(jb, expr)));
        opConv(F64_CONVERT_I64_S, (jb, expr) -> sExpr(Opcodes.L2D, expr.args));
        // TODO F64_CONVERT_I64_U

        opConv(F64_PROMOTE_F32, (jb, expr) -> sExpr(Opcodes.F2D, expr.args));

        opConv(I32_REINTERPRET_F32, (jb, expr) -> floatFn("floatToRawIntBits", "(F)I", expr.args));
        opConv(I64_REINTERPRET_F64, (jb, expr) -> doubleFn("doubleToRawLongBits", "(D)J", expr.args));
        opConv(F32_REINTERPRET_I32, (jb, expr) -> floatFn("intBitsToFloat", "(I)F", expr.args));
        opConv(F64_REINTERPRET_I64, (jb, expr) -> doubleFn("longBitsToDouble", "(J)D", expr.args));

        opConv(I32_EXTEND8_S, (jb, expr) -> sExpr(Opcodes.I2B, expr.args));
        opConv(I32_EXTEND16_S, (jb, expr) -> sExpr(Opcodes.I2S, expr.args));
        opConv(I64_EXTEND8_S, (jb, expr) -> sExpr(Opcodes.I2L, jb.buildInsn("int", sExpr(Opcodes.I2B, l2i(jb, expr.args[0])))));
        opConv(I64_EXTEND16_S, (jb, expr) -> sExpr(Opcodes.I2L, jb.buildInsn("int", sExpr(Opcodes.I2S, l2i(jb, expr.args[0])))));
        opConv(I64_EXTEND32_S, (jb, expr) -> sExpr(Opcodes.I2L, l2i(jb, expr.args[0])));

        iopConv(I32_TRUNC_F32_S, (jb, expr) -> sExpr(Opcodes.F2I, expr.args));
        iopConv(I32_TRUNC_F64_S, (jb, expr) -> sExpr(Opcodes.D2I, expr.args));
        iopConv(I64_TRUNC_F32_S, (jb, expr) -> sExpr(Opcodes.F2L, expr.args));
        iopConv(I64_TRUNC_F64_S, (jb, expr) -> sExpr(Opcodes.D2L, expr.args));
        // TODO I32_TRUNC_SAT_F32_U, I32_TRUNC_SAT_F64_U,
        // TODO I64_TRUNC_SAT_F32_U, I64_TRUNC_SAT_F64_U
    }
    // endregion conversions

    // endregion exprs

    private static <T, R> R convertUnchecked(JIRBuilder jb, T t) {
        WIRConvertHandler<?, ?> converter = CONVERTERS.get(t.getClass());
        if (converter == null) {
            throw new UnsupportedOperationException("converter does not exist for " + t.getClass());
        }
        return converter.convertUnchecked(jb, t);
    }

    private static void convertEffect(JIRBuilder jb, Effect effect) {
        convertUnchecked(jb, effect);
    }

    private static Control convertControl(JIRBuilder jb, Control effect) {
        return convertUnchecked(jb, effect);
    }

    private static Expr convertExpr(JIRBuilder jb, Expr expr) {
        return convertUnchecked(jb, expr);
    }

    private static AssignmentDest convertDest(JIRBuilder jb, AssignmentDest expr) {
        return CONVERTERS.get(expr.getClass()).convertUnchecked(jb, expr);
    }


    private static class FullConvertContext {
        private final Class theClass = new Class();
        private final ModuleNode module;
        public final List<Field> mems = new ArrayList<>(), globals = new ArrayList<>(), tables = new ArrayList<>();
        public final List<Method> funcs = new ArrayList<>();

        public FullConvertContext(ModuleNode node) {
            this.module = node;
        }

        private Method createConstructor() {
            Method constructor = new Method("<init>", Type.VOID_TYPE);
            Function func = constructor.func;
            BasicBlock rootBlock = new BasicBlock();
            func.blocks.add(rootBlock);

            // see https://webassembly.github.io/spec/core/exec/modules.html#instantiation
            JIRBuilder jb = new JIRBuilder(this, func);
            jb.setBb(rootBlock);
            jb.buildJump(new Control.Return(new Expr[0]));

            return constructor;
        }

        public Class convert() {
            if (module.funcs != null && module.funcs.funcs != null) {
                for (int i = 0; i < module.funcs.funcs.size(); i++) {
                    Method func = new Method("func" + i, Type.VOID_TYPE);
                    funcs.add(func);
                    theClass.methods.add(func);
                }
            }

            if (module.tables != null && module.tables.tables != null) {
                for (int i = 0; i < module.tables.tables.size(); i++) {
                    Field table = new Field("table" + i, Type.getType("[" + Type.getType(MethodHandle.class).getDescriptor()));
                    tables.add(table);
                    theClass.fields.add(table);
                }
            }

            if (module.mems != null && module.mems.memories != null) {
                for (int i = 0; i < module.mems.memories.size(); i++) {
                    Field mem = new Field("mem" + i, Type.getType(ByteBuffer.class));
                    mems.add(mem);
                    theClass.fields.add(mem);
                }
            }

            if (module.globals != null && module.globals.globals != null) {
                for (int i = 0; i < module.globals.globals.size(); i++) {
                    Field global = new Field("global" + i, Type.INT_TYPE);
                    globals.add(global);
                    theClass.fields.add(global); // FIXME correct type
                }
            }

            theClass.methods.add(createConstructor());

            if (module.codes != null) {
                int i = 0;
                for (CodeNode code : module.codes) {
                    Method method = theClass.methods.get(i++);
                    JIRBuilder jb = new JIRBuilder(this, method.func);
                    Function wirFunc = ((WIR.WIRExprNode) code.expr).wir;
                    for (BasicBlock block : wirFunc.blocks) {
                        jb.bbMap.put(block, jb.func.newBb());
                    }
                    try {
                        for (BasicBlock block : wirFunc.blocks) {
                            jb.setBb(jb.bbMap.get(block));
                            for (Effect effect : block.effects) {
                                convertEffect(jb, effect);
                            }
                            jb.buildJump(convertControl(jb, block.control));
                        }
                    } catch (UnsupportedOperationException e) {
                        e.printStackTrace();
                        jb.buildJump(new Control.Unreachable());
                    }
                }
            }

            return theClass;
        }

    }

    public static Class convertFromWir(ModuleNode wirAugmentedNode) {
        return new FullConvertContext(wirAugmentedNode).convert();
    }
}

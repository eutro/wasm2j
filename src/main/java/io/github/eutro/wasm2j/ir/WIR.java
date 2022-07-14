package io.github.eutro.wasm2j.ir;

import io.github.eutro.jwasm.BlockType;
import io.github.eutro.jwasm.Opcodes;
import io.github.eutro.jwasm.tree.*;
import io.github.eutro.wasm2j.InsnMap;
import io.github.eutro.wasm2j.ir.SSA.AssignmentDest.VarDest;
import io.github.eutro.wasm2j.ir.SSA.Effect.AssignmentStmt;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.eutro.jwasm.Opcodes.*;

public class WIR extends SSA {
    public enum WType {
        I32,
        I64,
        F32,
        F64,
        FUNCREF,
        EXTERNREF,
        ;

        public static WType fromByte(byte b) {
            switch (b) {
                case Opcodes.I32:
                    return I32;
                case Opcodes.I64:
                    return I64;
                case Opcodes.F32:
                    return F32;
                case Opcodes.F64:
                    return F64;
                case Opcodes.FUNCREF:
                    return FUNCREF;
                case Opcodes.EXTERNREF:
                    return EXTERNREF;
                default:
                    throw new IllegalArgumentException();
            }
        }

        public static WType[] fromBytes(byte... b) {
            WType[] types = new WType[b.length];
            for (int i = 0; i < types.length; i++) {
                types[i] = fromByte(b[i]);
            }
            return types;
        }
    }

    // dests

    public static class GlobalDest extends AssignmentDest {
        public final int var;

        public GlobalDest(int var) {
            this.var = var;
        }

        @Override
        public String toString() {
            return "globals[" + var + "]";
        }

        @Override
        public Collection<Expr> exprs() {
            return Collections.emptyList();
        }
    }

    public static class MemoryDest extends AssignmentDest {
        public final Expr address;
        public final int bytesStored;

        public MemoryDest(Expr address, int bytesStored) {
            this.address = address;
            this.bytesStored = bytesStored;
        }

        @Override
        public String toString() {
            return "mem[" + address + "..][0.." + bytesStored + "]";
        }

        @Override
        public Collection<Expr> exprs() {
            return Collections.singletonList(address);
        }
    }

    public static class TableDest extends AssignmentDest {
        public final int table;
        public final Expr index;

        public TableDest(int table, Expr index) {
            this.table = table;
            this.index = index;
        }

        @Override
        public String toString() {
            return "tables[" + table + "][" + index + "]";
        }

        @Override
        public Collection<Expr> exprs() {
            return Collections.singletonList(index);
        }
    }

    // exprs

    public static class GlobalRefExpr extends Expr {
        public final int var;

        public GlobalRefExpr(int var) {
            this.var = var;
        }

        @Override
        public String toString() {
            return "globals[" + var + "]";
        }

        @Override
        public Iterable<Expr> children() {
            return Collections.emptyList();
        }
    }

    public static class TableRefExpr extends Expr {
        public final int table;
        public final Expr index;

        public TableRefExpr(int table, Expr index) {
            this.table = table;
            this.index = index;
        }

        @Override
        public String toString() {
            return "tables[" + table + "][" + index + "]";
        }

        @Override
        public Iterable<Expr> children() {
            return Collections.singletonList(index);
        }
    }

    public static class ZeroInitExpr extends Expr {
        public final byte type;

        public ZeroInitExpr(byte type) {
            this.type = type;
        }

        @Override
        public Iterable<Expr> children() {
            return Collections.emptyList();
        }

        @Override
        public String toString() {
            return "zeroinit";
        }
    }

    public static class FuncRefExpr extends Expr {
        public final int func;

        public FuncRefExpr(int func) {
            this.func = func;
        }

        @Override
        public String toString() {
            return "funcs[" + func + "]";
        }

        @Override
        public Iterable<Expr> children() {
            return Collections.emptyList();
        }
    }

    public static class CallExpr extends Expr {
        public final int func;
        public final Expr[] args;

        public CallExpr(int func, Expr[] args) {
            this.func = func;
            this.args = args;
        }

        @Override
        public String toString() {
            return "funcs[" + func + "]" +
                    Arrays.stream(args)
                            .map(Objects::toString)
                            .collect(Collectors.joining(", ", "(", ")"));
        }

        @Override
        public Iterable<Expr> children() {
            return Arrays.asList(args);
        }
    }

    public static class CallIndirectExpr extends Expr {
        public final int type;
        public final Expr callee; // funcref already
        public final Expr[] args;

        public CallIndirectExpr(int type, Expr callee, Expr[] args) {
            this.type = type;
            this.callee = callee;
            this.args = args;
        }

        @Override
        public String toString() {
            return callee +
                    Arrays.stream(args)
                            .map(Objects::toString)
                            .collect(Collectors.joining(", ", "(", ")"));
        }

        @Override
        public Iterable<Expr> children() {
            return () -> Stream.concat(Stream.of(callee), Arrays.stream(args)).iterator();
        }
    }

    public static class MemorySizeExpr extends Expr {
        @Override
        public String toString() {
            return "mem.length";
        }

        @Override
        public Iterable<Expr> children() {
            return Collections.emptyList();
        }
    }

    public static class MemoryGrowExpr extends Expr {
        public final Expr size;

        public MemoryGrowExpr(Expr size) {
            this.size = size;
        }

        @Override
        public String toString() {
            return "mem = new Page[" + size + "]";
        }

        @Override
        public Iterable<Expr> children() {
            return Collections.singletonList(size);
        }
    }

    public static class DerefExpr extends Expr {
        public final Expr address;
        public final byte outType;
        public final int loadBytes;
        public final boolean extendUnsigned;

        public DerefExpr(Expr address, byte outType, int loadBytes, boolean extendUnsigned) {
            this.address = address;
            this.outType = outType;
            this.loadBytes = loadBytes;
            this.extendUnsigned = extendUnsigned;
        }

        @Override
        public String toString() {
            return String.format("(0x%02x)%s mem[%s..][0..%d]", outType, extendUnsigned ? "u" : "s", address, loadBytes);
        }

        @Override
        public Iterable<Expr> children() {
            return Collections.singletonList(address);
        }
    }

    public static class NullExpr extends Expr {
        public final byte type;

        public NullExpr(byte type) {
            this.type = type;
        }

        @Override
        public Iterable<Expr> children() {
            return Collections.emptyList();
        }

        @Override
        public String toString() {
            return "null";
        }
    }

    public static class IsNullExpr extends Expr {
        public final Expr value;

        public IsNullExpr(Expr value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value + "== null";
        }

        @Override
        public Iterable<Expr> children() {
            return Collections.singletonList(value);
        }
    }

    public static class SelectExpr extends Expr {
        public final Expr cond, ifT, ifF;

        public SelectExpr(Expr cond, Expr ifT, Expr ifF) {
            this.cond = cond;
            this.ifT = ifT;
            this.ifF = ifF;
        }

        @Override
        public String toString() {
            return cond + " ? " + ifT + " : " + ifF;
        }

        @Override
        public Iterable<Expr> children() {
            return Arrays.asList(cond, ifT, ifF);
        }
    }

    public static class OperatorExpr extends Expr {
        public final byte op;
        public final int intOp;
        public final byte returnType;
        public final Expr[] args;

        public OperatorExpr(byte op, int intOp, byte returnType, Expr[] args) {
            this.op = op;
            this.intOp = intOp;
            this.returnType = returnType;
            this.args = args;
        }

        @Override
        public String toString() {
            return "ops[" + op + "][" + intOp + "]" +
                    Arrays.stream(args)
                            .map(Objects::toString)
                            .collect(Collectors.joining(", ", "(", ")"));
        }

        @Override
        public Iterable<Expr> children() {
            return Arrays.asList(args);
        }
    }

    // conversion

    public static class ConvertState {
        // validator ops

        public static class CtrlFrame {
            public int opcode;
            public TypeNode type;
            public int height;
            public boolean unreachable;

            public BasicBlock firstBb;
            public BasicBlock bb;

            @Nullable
            public BasicBlock elseBb; // only present if opcode == IF
        }

        public int vals;
        public final List<CtrlFrame> ctrls = new ArrayList<>();

        public CtrlFrame ctrlsRef(int idx) {
            return ctrls.get(ctrls.size() - idx - 1);
        }

        public int pushV() {
            return ++vals;
        }

        public Var pushVar() {
            return refVar(pushV());
        }

        public int popV() {
            CtrlFrame frame = ctrlsRef(0);
            if (vals == frame.height
                    && /* should be true by validation anyway */ frame.unreachable) {
                return -1;
            }
            return vals--;
        }

        public Var popVar() {
            return refVar(popV());
        }

        public void popVs(int n) {
            for (int i = 0; i < n; i++) {
                popV();
            }
        }

        public CtrlFrame pushC(int opcode, TypeNode type, BasicBlock bb) {
            CtrlFrame frame = new CtrlFrame();
            frame.opcode = opcode;
            frame.type = type;
            frame.height = vals;
            frame.unreachable = false;
            frame.firstBb = frame.bb = bb;
            ctrls.add(frame);
            return frame;
        }

        public CtrlFrame popC() {
            CtrlFrame frame = ctrls.remove(ctrls.size() - 1);
            if (frame.unreachable) {
                vals = frame.height + frame.type.returns.length;
            } else {
                if (frame.height != vals - frame.type.returns.length) {
                    throw new RuntimeException("Frame height mismatch, likely a bug or an unverified module");
                }
            }
            return frame;
        }

        public int labelArity(CtrlFrame frame) {
            return (frame.opcode == LOOP ? frame.type.params : frame.type.returns).length;
        }

        public BasicBlock labelTarget(int depth) {
            CtrlFrame frame = ctrlsRef(depth);
            return frame.opcode == LOOP ? frame.firstBb : ctrlsRef(depth + 1).bb;
        }

        public void unreachable() {
            CtrlFrame frame = ctrlsRef(0);
            vals = frame.height;
            frame.unreachable = true;
            frame.bb = newBb();
        }

        // extensions

        public final byte[] localTypes;
        public final int argC, localC;
        public final Function func;
        public final TypeNode[] funcTypes;
        public final FuncNode[] referencableFuncs;
        public final int returns;
        public final int locals;
        public final List<Var> varVals = new ArrayList<>();

        public Var refVar(int n) {
            while (n >= varVals.size()) {
                varVals.add(func.newVar("stack" + (varVals.size() - locals)));
            }
            return varVals.get(n);
        }

        public BasicBlock newBb() {
            return func.newBb();
        }

        public TypeNode expand(BlockType blockType) {
            if (blockType.isValtype()) {
                return new TypeNode(
                        new byte[0],
                        blockType.get() == EMPTY_TYPE ? new byte[0] : new byte[]{(byte) blockType.get()}
                );
            } else {
                return funcTypes[blockType.get()];
            }
        }

        public void copyStack(
                BasicBlock bb,
                int arity,
                int from,
                int to
        ) {
            if (from == to) {
                return;
            }
            for (int i = 1; i <= arity; ++i) {
                bb.effects.add(new AssignmentStmt(
                        new Expr.VarExpr(refVar(from + i)),
                        new VarDest(refVar(to + i))
                ));
            }
        }

        public ConvertState(TypeNode[] funcTypes, FuncNode[] referencableFuncs, int argC, byte[] locals, int returns) {
            this.funcTypes = funcTypes;
            this.referencableFuncs = referencableFuncs;
            this.argC = argC;
            this.localTypes = locals;
            this.localC = locals.length;
            this.vals = this.locals = argC + localC;
            this.returns = returns;
            this.func = new Function();
        }
    }

    public interface Converter {
        void convert(ConvertState cs, AbstractInsnNode node);
    }

    public static InsnMap<Converter> CONVERTERS = new InsnMap<>();

    static {
        CONVERTERS.put(UNREACHABLE, (cs, node) -> {
            cs.ctrlsRef(0).bb.control = new Control.Unreachable();
            cs.unreachable();
        });
        CONVERTERS.put(NOP, (cs, node) -> {
        });
        CONVERTERS.put(new byte[]{
                BLOCK,
                LOOP
        }, (cs, node) -> {
            TypeNode type = cs.expand(((BlockInsnNode) node).blockType);
            ConvertState.CtrlFrame inFrame = cs.ctrlsRef(0);
            BasicBlock bb;
            if (node.opcode == BLOCK) {
                bb = inFrame.bb;
            } else {
                bb = cs.newBb();
                inFrame.bb.control = Control.Break.br(bb);
            }
            inFrame.bb = cs.newBb();
            cs.pushC(node.opcode, type, bb);
        });
        CONVERTERS.put(IF, (cs, node) -> {
            TypeNode type = cs.expand(((BlockInsnNode) node).blockType);
            ConvertState.CtrlFrame inFrame = cs.ctrlsRef(0);
            Var cond = cs.popVar();
            ConvertState.CtrlFrame newFrame = cs.pushC(node.opcode, type, cs.newBb());
            newFrame.elseBb = cs.newBb();
            inFrame.bb.control = Control.Break.brIf(
                    new Expr.VarExpr(cond),
                    newFrame.bb,
                    newFrame.elseBb
            );
            inFrame.bb = cs.newBb();
        });
        CONVERTERS.put(ELSE, (cs, node) -> {
            ConvertState.CtrlFrame frame = cs.ctrlsRef(0);
            cs.popVs(frame.type.returns.length);
            cs.vals += frame.type.params.length;
            frame.opcode = node.opcode;
            frame.unreachable = false;
            frame.bb.control = Control.Break.br(cs.ctrlsRef(1).bb);
            frame.bb = Objects.requireNonNull(frame.elseBb);
            frame.elseBb = null;
        });
    }

    private static void doReturn(ConvertState cs, BasicBlock bb) {
        Expr[] exprs = new Expr[cs.returns];
        for (int i = 0; i < exprs.length; i++) {
            exprs[i] = new Expr.VarExpr(cs.refVar(cs.vals + i + 1));
        }
        bb.control = new Control.Return(exprs);
    }

    static {
        CONVERTERS.put(END, (cs, node) -> {
            ConvertState.CtrlFrame frame = cs.popC();
            if (cs.ctrls.isEmpty()) {
                cs.vals = frame.height;
                doReturn(cs, frame.bb);
            } else {
                frame.bb.control = Control.Break.br(cs.ctrlsRef(0).bb);
                if (frame.elseBb != null) {
                    frame.elseBb.control = frame.bb.control;
                }
            }
            assert frame.bb.control != null;
        });

        CONVERTERS.put(BR, (cs, node) -> {
            ConvertState.CtrlFrame topFrame = cs.ctrlsRef(0);
            int depth = ((BreakInsnNode) node).label;
            ConvertState.CtrlFrame targetFrame = cs.ctrlsRef(depth);
            int arity = cs.labelArity(targetFrame);
            cs.popVs(arity);
            cs.copyStack(topFrame.bb, arity, cs.vals, targetFrame.height);
            topFrame.bb.control = Control.Break.br(cs.labelTarget(depth));
            cs.unreachable();
        });
        CONVERTERS.put(BR_IF, (cs, node) -> {
            BasicBlock thenBb = cs.newBb();
            BasicBlock elseBb = cs.newBb();
            ConvertState.CtrlFrame topFrame = cs.ctrlsRef(0);
            topFrame.bb.control = Control.Break.brIf(new Expr.VarExpr(cs.popVar()), thenBb, elseBb);
            topFrame.bb = elseBb;

            int depth = ((BreakInsnNode) node).label;
            ConvertState.CtrlFrame targetFrame = cs.ctrlsRef(depth);
            int arity = cs.labelArity(targetFrame);
            cs.copyStack(thenBb, arity, cs.vals - arity, targetFrame.height);
            thenBb.control = Control.Break.br(cs.labelTarget(depth));
        });
        CONVERTERS.put(BR_TABLE, (cs, node) -> {
            TableBreakInsnNode tblBr = (TableBreakInsnNode) node;
            BasicBlock[] bbs = new BasicBlock[tblBr.labels.length];
            BasicBlock elseBb = cs.newBb();

            ConvertState.CtrlFrame defaultFrame = cs.ctrlsRef(tblBr.defaultLabel);
            int arity = cs.labelArity(defaultFrame);
            Var condVal = cs.popVar();
            cs.popVs(arity);

            cs.copyStack(elseBb, arity, cs.vals, defaultFrame.height);
            elseBb.control = Control.Break.br(cs.labelTarget(tblBr.defaultLabel));
            for (int i = 0; i < bbs.length; i++) {
                BasicBlock bb = cs.newBb();
                ConvertState.CtrlFrame frame = cs.ctrlsRef(tblBr.labels[i]);
                cs.copyStack(bb, arity, cs.vals, frame.height);
                bb.control = Control.Break.br(cs.labelTarget(tblBr.labels[i]));
                bbs[i] = bb;
            }

            ConvertState.CtrlFrame topFrame = cs.ctrlsRef(0);
            topFrame.bb.control = new Control.Break(bbs, elseBb, new Expr.VarExpr(condVal));
            cs.unreachable();
        });
        CONVERTERS.put(RETURN, (cs, node) -> {
            cs.popVs(cs.returns);
            doReturn(cs, cs.ctrlsRef(0).bb);
            cs.unreachable();
        });
        CONVERTERS.put(new byte[]{
                CALL,
                CALL_INDIRECT,
        }, (cs, node) -> {
            BasicBlock bb = cs.ctrlsRef(0).bb;

            TypeNode type;
            CallInsnNode callInsn = null;
            Expr callee = null;
            if (node.opcode == CALL) {
                callInsn = (CallInsnNode) node;
                FuncNode func = cs.referencableFuncs[callInsn.function];
                type = cs.funcTypes[func.type];
            } else {
                CallIndirectInsnNode ciNode = (CallIndirectInsnNode) node;
                callee = new TableRefExpr(ciNode.table, new Expr.VarExpr(cs.popVar()));
                type = cs.funcTypes[ciNode.type];
            }
            Expr[] args = new Expr[type.params.length];
            cs.popVs(args.length);
            for (int i = 0; i < args.length; i++) {
                args[i] = new Expr.VarExpr(cs.refVar(cs.vals + i + 1));
            }
            Expr call;
            if (node.opcode == CALL) {
                call = new CallExpr(callInsn.function, args);
            } else {
                CallIndirectInsnNode callNode = (CallIndirectInsnNode) node;
                call = new CallIndirectExpr(
                        callNode.type,
                        callee,
                        args
                );
            }
            if (type.returns.length == 1) {
                bb.effects.add(new AssignmentStmt(call, new VarDest(cs.pushVar())));
            } else {
                AssignmentDest[] assignees = new AssignmentDest[type.returns.length];
                for (int i = 0; i < assignees.length; i++) {
                    assignees[i] = new VarDest(cs.pushVar());
                }
                bb.effects.add(new Effect.AssignManyStmt(assignees, call));
            }
        });
    }

    static {
        CONVERTERS.put(REF_NULL, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new AssignmentStmt(
                new NullExpr(((NullInsnNode) node).type),
                new VarDest(cs.pushVar())
        )));
        CONVERTERS.put(REF_IS_NULL, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new AssignmentStmt(
                new IsNullExpr(new Expr.VarExpr(cs.popVar())),
                new VarDest(cs.pushVar())
        )));
        CONVERTERS.put(REF_FUNC, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new AssignmentStmt(
                new FuncRefExpr(((FuncRefInsnNode) node).function),
                new VarDest(cs.pushVar())
        )));
    }

    static {
        CONVERTERS.put(DROP, (cs, node) -> cs.popV());
        CONVERTERS.put(new byte[]{
                SELECT,
                SELECTT
        }, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new AssignmentStmt(
                new SelectExpr(
                        new Expr.VarExpr(cs.popVar()), // cond
                        new Expr.VarExpr(cs.popVar()),
                        new Expr.VarExpr(cs.popVar())
                ),
                new VarDest(cs.pushVar())
        )));
    }

    static {
        CONVERTERS.put(LOCAL_GET, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new AssignmentStmt(
                new Expr.VarExpr(cs.refVar(((VariableInsnNode) node).variable)),
                new VarDest(cs.pushVar())
        )));
        CONVERTERS.put(new byte[]{
                LOCAL_SET,
                LOCAL_TEE
        }, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new AssignmentStmt(
                new Expr.VarExpr(cs.refVar(node.opcode == LOCAL_SET ? cs.popV() : cs.vals)),
                new VarDest(cs.refVar(((VariableInsnNode) node).variable))
        )));
        CONVERTERS.put(GLOBAL_GET, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new AssignmentStmt(
                new GlobalRefExpr(((VariableInsnNode) node).variable),
                new VarDest(cs.pushVar())
        )));
        CONVERTERS.put(GLOBAL_SET, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new AssignmentStmt(
                new Expr.VarExpr(cs.popVar()),
                new GlobalDest(((VariableInsnNode) node).variable)
        )));
    }

    static {
        CONVERTERS.put(TABLE_GET, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new AssignmentStmt(
                new TableRefExpr(((TableInsnNode) node).table, new Expr.VarExpr(cs.popVar())),
                new VarDest(cs.pushVar())
        )));
        CONVERTERS.put(TABLE_SET, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new AssignmentStmt(
                new Expr.VarExpr(cs.popVar()),
                new TableDest(((TableInsnNode) node).table,
                        new Expr.VarExpr(cs.popVar()))
        )));
        CONVERTERS.putInt(new int[]{
                TABLE_INIT,
                ELEM_DROP,
                TABLE_COPY,
                TABLE_GROW,
                TABLE_SIZE,
                TABLE_FILL // TODO
        }, (cs, node) -> {
            throw new UnsupportedOperationException();
        });
    }

    private static Converter makeLoadInsn(byte outType, int bytesRead, boolean extUnsigned) {
        return (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new AssignmentStmt(
                new DerefExpr(
                        new Expr.VarExpr(cs.popVar()),
                        outType,
                        bytesRead,
                        extUnsigned
                ),
                new VarDest(cs.pushVar())
        ));
    }

    static {
        CONVERTERS.put(I32_LOAD, makeLoadInsn(I32, 4, false));
        CONVERTERS.put(I64_LOAD, makeLoadInsn(I64, 8, false));
        CONVERTERS.put(F32_LOAD, makeLoadInsn(F32, 4, false));
        CONVERTERS.put(F64_LOAD, makeLoadInsn(F64, 8, false));

        CONVERTERS.put(I32_LOAD8_S, makeLoadInsn(I32, 1, false));
        CONVERTERS.put(I32_LOAD8_U, makeLoadInsn(I32, 1, true));
        CONVERTERS.put(I32_LOAD16_S, makeLoadInsn(I32, 2, false));
        CONVERTERS.put(I32_LOAD16_U, makeLoadInsn(I32, 2, true));

        CONVERTERS.put(I64_LOAD8_S, makeLoadInsn(I64, 1, false));
        CONVERTERS.put(I64_LOAD8_U, makeLoadInsn(I64, 1, true));
        CONVERTERS.put(I64_LOAD16_S, makeLoadInsn(I64, 2, false));
        CONVERTERS.put(I64_LOAD16_U, makeLoadInsn(I64, 2, true));
        CONVERTERS.put(I64_LOAD32_S, makeLoadInsn(I64, 4, false));
        CONVERTERS.put(I64_LOAD32_U, makeLoadInsn(I64, 4, true));
    }

    private static Converter makeStoreInsn(int bytesWritten) {
        return (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new AssignmentStmt(
                new Expr.VarExpr(cs.popVar()),
                new MemoryDest(new Expr.VarExpr(cs.popVar()), bytesWritten)
        ));
    }

    static {
        CONVERTERS.put(I32_STORE, makeStoreInsn(4));
        CONVERTERS.put(I64_STORE, makeStoreInsn(8));
        CONVERTERS.put(F32_STORE, makeStoreInsn(4));
        CONVERTERS.put(F64_STORE, makeStoreInsn(8));

        CONVERTERS.put(I32_STORE8, makeStoreInsn(1));
        CONVERTERS.put(I32_STORE16, makeStoreInsn(2));

        CONVERTERS.put(I64_STORE8, makeStoreInsn(1));
        CONVERTERS.put(I64_STORE16, makeStoreInsn(2));
        CONVERTERS.put(I64_STORE32, makeStoreInsn(4));
    }

    static {
        CONVERTERS.put(MEMORY_SIZE, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new AssignmentStmt(
                new MemorySizeExpr(),
                new VarDest(cs.pushVar())
        )));
        CONVERTERS.put(MEMORY_GROW, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new AssignmentStmt(
                new MemoryGrowExpr(new Expr.VarExpr(cs.popVar())),
                new VarDest(cs.pushVar())
        )));
        CONVERTERS.putInt(new int[]{
                MEMORY_INIT,
                DATA_DROP,
                MEMORY_COPY,
                MEMORY_FILL,
        }, (cs, node) -> {
            throw new UnsupportedOperationException();
        });
    }

    static {
        CONVERTERS.put(new byte[]{
                I32_CONST,
                I64_CONST,
                F32_CONST,
                F64_CONST,
        }, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new AssignmentStmt(
                new Expr.ConstExpr(((ConstInsnNode) node).value),
                new VarDest(cs.pushVar())
        )));
    }

    public static Converter makeOpInsn(int arity, byte returnType) {
        return (cs, node) -> {
            cs.popVs(arity);
            Expr[] args = new Expr[arity];
            for (int i = 0; i < args.length; i++) {
                args[i] = new Expr.VarExpr(cs.refVar(cs.vals + i + 1));
            }
            cs.ctrlsRef(0).bb.effects.add(new AssignmentStmt(
                    new OperatorExpr(
                            node.opcode,
                            node instanceof PrefixInsnNode ? ((PrefixInsnNode) node).intOpcode : 0,
                            returnType,
                            args
                    ),
                    new VarDest(cs.pushVar())
            ));
        };
    }

    static {
        // comparisons
        {
            CONVERTERS.put(I32_EQZ, makeOpInsn(1, I32));
            CONVERTERS.put(new byte[]{
                    I32_EQ,
                    I32_NE,
                    I32_LT_S,
                    I32_LT_U,
                    I32_GT_S,
                    I32_GT_U,
                    I32_LE_S,
                    I32_LE_U,
                    I32_GE_S,
                    I32_GE_U,
            }, makeOpInsn(2, I32));

            CONVERTERS.put(I64_EQZ, makeOpInsn(1, I32));
            CONVERTERS.put(new byte[]{
                    I64_EQ,
                    I64_NE,
                    I64_LT_S,
                    I64_LT_U,
                    I64_GT_S,
                    I64_GT_U,
                    I64_LE_S,
                    I64_LE_U,
                    I64_GE_S,
                    I64_GE_U,
            }, makeOpInsn(2, I32));

            CONVERTERS.put(new byte[]{
                    F32_EQ,
                    F32_NE,
                    F32_LT,
                    F32_GT,
                    F32_LE,
                    F32_GE,
            }, makeOpInsn(2, I32));

            CONVERTERS.put(new byte[]{
                    F64_EQ,
                    F64_NE,
                    F64_LT,
                    F64_GT,
                    F64_LE,
                    F64_GE,
            }, makeOpInsn(2, I32));
        }

        // maths
        {
            CONVERTERS.put(new byte[]{
                    I32_CLZ,
                    I32_CTZ,
                    I32_POPCNT,
            }, makeOpInsn(1, I32));
            CONVERTERS.put(new byte[]{
                    I32_ADD,
                    I32_SUB,
                    I32_MUL,
                    I32_DIV_S,
                    I32_DIV_U,
                    I32_REM_S,
                    I32_REM_U,
                    I32_AND,
                    I32_OR,
                    I32_XOR,
                    I32_SHL,
                    I32_SHR_S,
                    I32_SHR_U,
                    I32_ROTL,
                    I32_ROTR,
            }, makeOpInsn(2, I32));

            CONVERTERS.put(new byte[]{
                    I64_CLZ,
                    I64_CTZ,
                    I64_POPCNT,
            }, makeOpInsn(1, I64));
            CONVERTERS.put(new byte[]{
                    I64_ADD,
                    I64_SUB,
                    I64_MUL,
                    I64_DIV_S,
                    I64_DIV_U,
                    I64_REM_S,
                    I64_REM_U,
                    I64_AND,
                    I64_OR,
                    I64_XOR,
                    I64_SHL,
                    I64_SHR_S,
                    I64_SHR_U,
                    I64_ROTL,
                    I64_ROTR,
            }, makeOpInsn(2, I64));

            CONVERTERS.put(new byte[]{
                    F32_ABS,
                    F32_NEG,
                    F32_CEIL,
                    F32_FLOOR,
                    F32_TRUNC,
                    F32_NEAREST,
                    F32_SQRT,
            }, makeOpInsn(1, F32));
            CONVERTERS.put(new byte[]{
                    F32_ADD,
                    F32_SUB,
                    F32_MUL,
                    F32_DIV,
                    F32_MIN,
                    F32_MAX,
                    F32_COPYSIGN,
            }, makeOpInsn(2, F32));

            CONVERTERS.put(new byte[]{
                    F64_ABS,
                    F64_NEG,
                    F64_CEIL,
                    F64_FLOOR,
                    F64_TRUNC,
                    F64_NEAREST,
                    F64_SQRT,
            }, makeOpInsn(1, F64));
            CONVERTERS.put(new byte[]{
                    F64_ADD,
                    F64_SUB,
                    F64_MUL,
                    F64_DIV,
                    F64_MIN,
                    F64_MAX,
                    F64_COPYSIGN,
            }, makeOpInsn(2, F64));
        }

        // conversions
        {
            CONVERTERS.put(new byte[]{
                    I32_WRAP_I64,
                    I32_TRUNC_F32_S,
                    I32_TRUNC_F32_U,
                    I32_TRUNC_F64_S,
                    I32_TRUNC_F64_U,
            }, makeOpInsn(1, I32));
            CONVERTERS.put(new byte[]{
                    I64_EXTEND_I32_S,
                    I64_EXTEND_I32_U,
                    I64_TRUNC_F32_S,
                    I64_TRUNC_F32_U,
                    I64_TRUNC_F64_S,
                    I64_TRUNC_F64_U,
            }, makeOpInsn(1, I64));
            CONVERTERS.put(new byte[]{
                    F32_CONVERT_I32_S,
                    F32_CONVERT_I32_U,
                    F32_CONVERT_I64_S,
                    F32_CONVERT_I64_U,
                    F32_DEMOTE_F64,
            }, makeOpInsn(1, F32));
            CONVERTERS.put(new byte[]{
                    F64_CONVERT_I32_S,
                    F64_CONVERT_I32_U,
                    F64_CONVERT_I64_S,
                    F64_CONVERT_I64_U,
                    F64_PROMOTE_F32,
            }, makeOpInsn(1, F64));
            CONVERTERS.put(I32_REINTERPRET_F32, makeOpInsn(1, I32));
            CONVERTERS.put(I64_REINTERPRET_F64, makeOpInsn(1, I64));
            CONVERTERS.put(F32_REINTERPRET_I32, makeOpInsn(1, F32));
            CONVERTERS.put(F64_REINTERPRET_I64, makeOpInsn(1, F64));
        }

        // extension
        {
            CONVERTERS.put(new byte[]{
                    I32_EXTEND8_S,
                    I32_EXTEND16_S,
            }, makeOpInsn(1, I32));
            CONVERTERS.put(new byte[]{
                    I64_EXTEND8_S,
                    I64_EXTEND16_S,
                    I64_EXTEND32_S,
            }, makeOpInsn(1, I64));
        }

        // saturating truncation
        {
            CONVERTERS.putInt(new int[]{
                    I32_TRUNC_SAT_F32_S,
                    I32_TRUNC_SAT_F32_U,
                    I32_TRUNC_SAT_F64_S,
                    I32_TRUNC_SAT_F64_U,
            }, makeOpInsn(1, I32));

            CONVERTERS.putInt(new int[]{
                    I64_TRUNC_SAT_F32_S,
                    I64_TRUNC_SAT_F32_U,
                    I64_TRUNC_SAT_F64_S,
                    I64_TRUNC_SAT_F64_U,
            }, makeOpInsn(1, I64));
        }
    }

    public static class WIRExprNode extends ExprNode {
        public final Function wir;

        public WIRExprNode(Function wir) {
            this.wir = wir;
        }
    }

    private static class FullConvertState {
        private final ModuleNode node;
        private final TypeNode[] funcTypes;
        private final FuncNode[] referencableFuncs;

        public FullConvertState(ModuleNode node) {
            this.node = node;
            TypeNode[] funcTypes = new TypeNode[0];
            if (node.types != null && node.types.types != null) {
                funcTypes = node.types.types.toArray(funcTypes);
            }
            this.funcTypes = funcTypes;
            List<FuncNode> referencableFuncs = new ArrayList<>();
            if (node.imports != null) {
                for (AbstractImportNode importNode : node.imports) {
                    if (importNode instanceof FuncImportNode) {
                        referencableFuncs.add(new FuncNode(((FuncImportNode) importNode).type));
                    }
                }
            }
            if (node.funcs != null && node.funcs.funcs != null) {
                referencableFuncs.addAll(node.funcs.funcs);
            }
            this.referencableFuncs = referencableFuncs.toArray(new FuncNode[0]);
        }

        public WIRExprNode replaceExprNode(
                TypeNode funcType,
                byte[] locals,
                ExprNode expr
        ) {
            ConvertState cs = new ConvertState(
                    funcTypes,
                    referencableFuncs,
                    funcType.params.length,
                    locals,
                    funcType.returns.length
            );
            WIRExprNode node = new WIRExprNode(convertFunc(cs, expr));
            if (expr.instructions != null) {
                node.instructions = new LinkedList<>(expr.instructions);
            }
            inferTypes(funcType, node.wir);
            return node;
        }

        public Function convertFunc(
                ConvertState cs,
                ExprNode expr
        ) {
            cs.pushC(END, new TypeNode(new byte[0], new byte[cs.returns]), cs.newBb());
            BasicBlock firstBb = cs.ctrlsRef(0).bb;
            for (int i = 0; i < cs.argC; i++) {
                Var argVar = cs.func.newVar("arg" + i);
                cs.varVals.add(argVar);
                firstBb.effects.add(new AssignmentStmt(
                        new Expr.FuncArgExpr(i),
                        new VarDest(argVar)
                ));
            }
            for (int i = 0; i < cs.localC; ++i) {
                Var localVar = cs.func.newVar("local" + i);
                cs.varVals.add(localVar);
                firstBb.effects.add(new AssignmentStmt(
                        new ZeroInitExpr(cs.localTypes[i]),
                        new VarDest(localVar)
                ));
            }
            for (AbstractInsnNode insn : expr) {
                Converter converter = CONVERTERS.get(insn);
                if (converter == null) {
                    throw new UnsupportedOperationException("Instruction not supported");
                }
                converter.convert(cs, insn);
            }
            Function func = cs.func;
            opt0(func);
            computeDoms(func);
            computeDomFrontier(func);
            assignVariables(func);
            return func;
        }

        private void inferTypes(
                TypeNode funcType,
                Function func
        ) {
            Set<BasicBlock> visited = new HashSet<>();
            class S {
                WType[] typeOf(Expr expr) {
                    // blame Java not me for this instanceof else-if chain
                    if (expr instanceof Expr.VarExpr) {
                        Object objType = ((Expr.VarExpr) expr).var.type;
                        if (!(objType instanceof WType)) {
                            // we walk the graph in depth-first order of jumps,
                            // so any fix variables are assigned in a dominator (which has been seen, by definition),
                            // and any phi arguments have been assigned by the time we jump from a seen preceding block
                            throw new IllegalStateException();
                        }
                        return new WType[]{(WType) objType};
                    } else if (expr instanceof Expr.FuncArgExpr) {
                        return WType.fromBytes(funcType.params[((Expr.FuncArgExpr) expr).arg]);
                    } else if (expr instanceof Expr.ConstExpr) {
                        Object val = ((Expr.ConstExpr) expr).val;
                        WType ty;
                        if (val instanceof Integer) ty = WType.I32;
                        else if (val instanceof Long) ty = WType.I64;
                        else if (val instanceof Float) ty = WType.F32;
                        else if (val instanceof Double) ty = WType.F64;
                        else throw new AssertionError();
                        return new WType[]{ty};
                    } else if (expr instanceof Expr.PhiExpr) {
                        Map<BasicBlock, Expr> branches = ((Expr.PhiExpr) expr).branches;
                        for (Map.Entry<BasicBlock, Expr> entry : branches.entrySet()) {
                            if (visited.contains(entry.getKey())) {
                                return typeOf(entry.getValue());
                            }
                        }
                        // at least one of the predecessors must have been seen already
                        // so this should be unreachable
                        throw new IllegalStateException();
                    } else if (expr instanceof GlobalRefExpr) {
                        assert node.globals != null;
                        assert node.globals.globals != null;
                        GlobalNode global = node.globals.globals.get(((GlobalRefExpr) expr).var);
                        return WType.fromBytes(global.type.type);
                    } else if (expr instanceof TableRefExpr) {
                        assert node.tables != null;
                        assert node.tables.tables != null;
                        TableNode table = node.tables.tables.get(((TableRefExpr) expr).table);
                        return WType.fromBytes(table.type);
                    } else if (expr instanceof ZeroInitExpr) {
                        return WType.fromBytes(((ZeroInitExpr) expr).type);
                    } else if (expr instanceof FuncRefExpr) {
                        return new WType[]{WType.FUNCREF};
                    } else if (expr instanceof CallExpr) {
                        TypeNode type = funcTypes[referencableFuncs[((CallExpr) expr).func].type];
                        return WType.fromBytes(type.returns);
                    } else if (expr instanceof CallIndirectExpr) {
                        TypeNode type = funcTypes[((CallIndirectExpr) expr).type];
                        return WType.fromBytes(type.returns);
                    } else if (
                            expr instanceof MemorySizeExpr
                                    || expr instanceof MemoryGrowExpr
                                    || expr instanceof IsNullExpr
                    ) {
                        return new WType[]{WType.I32};
                    } else if (expr instanceof DerefExpr) {
                        return WType.fromBytes(((DerefExpr) expr).outType);
                    } else if (expr instanceof NullExpr) {
                        return WType.fromBytes(((NullExpr) expr).type);
                    } else if (expr instanceof SelectExpr) {
                        return typeOf(((SelectExpr) expr).ifT);
                    } else if (expr instanceof OperatorExpr) {
                        return WType.fromBytes(((OperatorExpr) expr).returnType);
                    } else {
                        throw new IllegalArgumentException("unkown expr type");
                    }
                }

                void processBlock(BasicBlock block) {
                    for (Effect effect : block.effects) {
                        List<AssignmentDest> dests = effect.dests();
                        if (dests.isEmpty()) continue;
                        WType[] types = typeOf(effect.expr());
                        if (types.length != dests.size()) throw new IllegalStateException();
                        int i = 0;
                        for (AssignmentDest dest : dests) {
                            if (dest instanceof VarDest) {
                                ((VarDest) dest).var.type = types[i];
                            }
                            i++;
                        }
                    }
                }
            }
            S s = new S();

            Set<BasicBlock> seen = new HashSet<>();
            List<BasicBlock> stack = new ArrayList<>();
            BasicBlock root = func.blocks.get(0);
            stack.add(root);
            seen.add(root);
            while (!stack.isEmpty()) {
                BasicBlock block = stack.remove(stack.size() - 1);
                s.processBlock(block);
                visited.add(block);
                for (BasicBlock target : block.control.targets()) {
                    if (seen.add(target)) {
                        stack.add(target);
                    }
                }
            }
        }
    }

    public static void augmentWithWir(ModuleNode node) {
        FullConvertState state = new FullConvertState(node);
        if (node.globals != null) {
            for (GlobalNode global : node.globals) {
                global.init = state.replaceExprNode(
                        new TypeNode(new byte[0], new byte[]{global.type.type}),
                        new byte[0],
                        global.init
                );
            }
        }

        if (node.datas != null) {
            for (DataNode data : node.datas) {
                if (data.offset != null) {
                    data.offset = state.replaceExprNode(
                            new TypeNode(new byte[0], new byte[]{I32}),
                            new byte[0],
                            data.offset
                    );
                }
            }
        }

        if (node.codes != null) {
            int i = 0;
            for (CodeNode code : node.codes) {
                assert node.funcs != null && node.funcs.funcs != null;
                TypeNode type = state.funcTypes[node.funcs.funcs.get(i).type];
                code.expr = state.replaceExprNode(
                        type,
                        code.locals,
                        code.expr
                );
                i++;
            }

            if (node.elems != null) {
                for (ElementNode elem : node.elems) {
                    elem.offset = state.replaceExprNode(
                            new TypeNode(new byte[0], new byte[]{elem.type}),
                            new byte[0],
                            elem.offset
                    );
                }
            }
        }
    }
}

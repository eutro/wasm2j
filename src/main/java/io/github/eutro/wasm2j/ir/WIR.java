package io.github.eutro.wasm2j.ir;

import io.github.eutro.jwasm.BlockType;
import io.github.eutro.jwasm.tree.*;
import io.github.eutro.wasm2j.InsnMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.eutro.jwasm.Opcodes.*;

public class WIR {
    public static class Var {
        public String name;

        public Var(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static class Function {
        public List<BasicBlock> blocks = new ArrayList<>();
        public Map<String, Set<Var>> vars = new HashMap<>();

        public Var newVar(String name) {
            Var var;
            if (vars.containsKey(name)) {
                Set<Var> varSet = vars.get(name);
                if (name.isEmpty()) {
                    name = Integer.toString(varSet.size());
                } else {
                    name += "." + varSet.size();
                }
                var = new Var(name);
                varSet.add(var);
            } else {
                var = new Var(name);
                HashSet<Var> set = new HashSet<>();
                set.add(var);
                vars.put(name, set);
            }
            return var;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("fn() {\n");
            for (BasicBlock block : blocks) {
                sb.append(block).append('\n');
            }
            sb.append("}");
            return sb.toString();
        }
    }

    public static class BasicBlock {
        public final List<Effect> effects = new ArrayList<>();
        public Control control;

        public BasicBlock idom;
        public List<BasicBlock> preds;
        public Set<BasicBlock> domFrontier;

        public Object data;

        public String toTargetString() {
            return String.format("@%08x", System.identityHashCode(this));
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(toTargetString()).append("{\n");
            for (Effect effect : effects) {
                sb.append(' ').append(effect).append('\n');
            }
            sb.append(' ').append(control);
            sb.append("\n}");
            return sb.toString();
        }
    }

    public static abstract class Control {
        public abstract Iterable<Expr> exprs();

        public static class Break extends Control {
            public final BasicBlock[] targets;
            public BasicBlock dfltTarget;
            @Nullable
            public Expr cond;

            public Break(BasicBlock[] targets, BasicBlock dfltTarget, @Nullable Expr cond) {
                this.targets = targets;
                this.dfltTarget = dfltTarget;
                this.cond = cond;
            }

            public static Break br(BasicBlock target) {
                return new Break(new BasicBlock[0], target, null);
            }

            public static Break brIf(Expr expr, BasicBlock ifT, BasicBlock ifF) {
                return new Break(new BasicBlock[]{ifF}, ifT, expr);
            }

            @Override
            public String toString() {
                return targets.length == 0
                        ? "br " + dfltTarget.toTargetString()
                        : targets.length == 1
                        ? "br_if " + cond + " " + dfltTarget.toTargetString() + " " + targets[0].toTargetString()
                        : "br_table "
                        + Arrays.stream(targets).map(BasicBlock::toTargetString).collect(Collectors.joining(", ", "[", "]"))
                        + "[" + cond + "] ?? " + dfltTarget.toTargetString();
            }

            @Override
            public Iterable<Expr> exprs() {
                return cond == null ? Collections.emptyList() : Collections.singletonList(cond);
            }
        }

        public static class Return extends Control {
            public final Expr[] values;

            public Return(Expr[] values) {
                this.values = values;
            }

            @Override
            public String toString() {
                return "return " + Arrays.toString(values);
            }

            @Override
            public Iterable<Expr> exprs() {
                return Arrays.asList(values);
            }
        }

        public static class Unreachable extends Control {
            @Override
            public String toString() {
                return "unreachable";
            }

            @Override
            public Iterable<Expr> exprs() {
                return Collections.emptyList();
            }
        }
    }

    public static abstract class Effect {
        public abstract Expr expr();

        public abstract List<AssignmentDest> dests();

        public static class AssignmentStmt extends Effect {
            public final AssignmentDest dest;
            public final Expr value;

            public AssignmentStmt(Expr value, AssignmentDest dest) {
                this.dest = dest;
                this.value = value;
            }

            @Override
            public String toString() {
                return dest + " = " + value;
            }

            @Override
            public Expr expr() {
                return value;
            }

            @Override
            public List<AssignmentDest> dests() {
                return Collections.singletonList(dest);
            }
        }

        public static class AssignManyStmt extends Effect {
            public final AssignmentDest[] dest;
            public final Expr value;

            public AssignManyStmt(AssignmentDest[] dest, Expr value) {
                this.dest = dest;
                this.value = value;
            }

            @Override
            public String toString() {
                return Arrays.toString(dest) + " = " + value;
            }

            @Override
            public Expr expr() {
                return value;
            }

            @Override
            public List<AssignmentDest> dests() {
                return Arrays.asList(dest);
            }
        }
    }

    public static abstract class AssignmentDest {
        public abstract Collection<Expr> exprs();

        public static class VarDest extends AssignmentDest {
            public Var var;

            public VarDest(Var var) {
                this.var = var;
            }

            @Override
            public String toString() {
                return "$" + var;
            }

            @Override
            public Collection<Expr> exprs() {
                return Collections.emptyList();
            }
        }

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
    }

    public static abstract class Expr {
        public abstract Iterable<Expr> children();

        public static class VarExpr extends Expr {
            public Var var;

            public VarExpr(Var var) {
                this.var = var;
            }

            @Override
            public String toString() {
                return "$" + var;
            }

            @Override
            public Iterable<Expr> children() {
                return Collections.emptyList();
            }
        }

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

        public static class FuncArgExpr extends Expr {
            public final int arg;

            public FuncArgExpr(int arg) {
                this.arg = arg;
            }

            @Override
            public Iterable<Expr> children() {
                return Collections.emptyList();
            }

            @Override
            public String toString() {
                return "args[" + arg + "]";
            }
        }

        public static class ZeroInitExpr extends Expr {
            @Override
            public Iterable<Expr> children() {
                return Collections.emptyList();
            }

            @Override
            public String toString() {
                return "zeroinit";
            }
        }

        public static class ConstExpr extends Expr {
            public final Object val;

            public ConstExpr(Object val) {
                this.val = val;
            }

            @Override
            public String toString() {
                return Objects.toString(val);
            }

            @Override
            public Iterable<Expr> children() {
                return Collections.emptyList();
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
            public final Expr callee;
            public final Expr[] args;

            public CallIndirectExpr(Expr callee, Expr[] args) {
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
            public final Expr[] args;

            public OperatorExpr(byte op, int intOp, Expr[] args) {
                this.op = op;
                this.intOp = intOp;
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

        public static class PhiExpr extends Expr {
            public Map<BasicBlock, Expr> branches;

            public PhiExpr(Map<BasicBlock, Expr> branches) {
                this.branches = branches;
            }

            @Override
            public Iterable<Expr> children() {
                return branches.values();
            }

            @Override
            public String toString() {
                return branches
                        .entrySet()
                        .stream()
                        .map(entry -> entry.getKey().toTargetString() + ": " + entry.getValue())
                        .collect(Collectors.joining(", ", "phi(", ")"));
            }
        }
    }

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
            vals -= n;
        }

        public CtrlFrame pushC(int opcode, TypeNode type) {
            CtrlFrame frame = new CtrlFrame();
            frame.opcode = opcode;
            frame.type = type;
            frame.height = vals;
            frame.unreachable = false;
            frame.firstBb = frame.bb = newBb();
            ctrls.add(frame);
            return frame;
        }

        public CtrlFrame popC() {
            CtrlFrame frame = ctrls.remove(ctrls.size() - 1);
            if (!frame.unreachable && frame.height != vals - frame.type.returns.length) {
                throw new RuntimeException("Frame height mismatch, likely a bug or an unverified module");
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
            BasicBlock bb = new BasicBlock();
            func.blocks.add(bb);
            return bb;
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
                bb.effects.add(new Effect.AssignmentStmt(
                        new Expr.VarExpr(refVar(from + i)),
                        new AssignmentDest.VarDest(refVar(to + i))
                ));
            }
        }

        public ConvertState(TypeNode[] funcTypes, FuncNode[] referencableFuncs, int stackDepth, int returns) {
            this.funcTypes = funcTypes;
            this.referencableFuncs = referencableFuncs;
            this.locals = stackDepth;
            this.returns = returns;
            this.func = new Function();
            this.vals = stackDepth;
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
            ConvertState.CtrlFrame newFrame = cs.pushC(node.opcode, type);
            inFrame.bb.control = Control.Break.br(newFrame.bb);
            inFrame.bb = cs.newBb();
        });
        CONVERTERS.put(IF, (cs, node) -> {
            TypeNode type = cs.expand(((BlockInsnNode) node).blockType);
            ConvertState.CtrlFrame inFrame = cs.ctrlsRef(0);
            Var cond = cs.popVar();
            ConvertState.CtrlFrame newFrame = cs.pushC(node.opcode, type);
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
        cs.popVs(cs.returns);
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
                callee = new Expr.TableRefExpr(ciNode.table, new Expr.VarExpr(cs.popVar()));
                type = cs.funcTypes[ciNode.type];
            }
            Expr[] args = new Expr[type.params.length];
            cs.popVs(args.length);
            for (int i = 0; i < args.length; i++) {
                args[i] = new Expr.VarExpr(cs.refVar(cs.vals + i + 1));
            }
            Expr call;
            if (node.opcode == CALL) {
                call = new Expr.CallExpr(callInsn.function, args);
            } else {
                call = new Expr.CallIndirectExpr(callee, args);
            }
            if (type.returns.length == 1) {
                bb.effects.add(new Effect.AssignmentStmt(call, new AssignmentDest.VarDest(cs.pushVar())));
            } else {
                AssignmentDest[] assignees = new AssignmentDest[type.returns.length];
                for (int i = 0; i < assignees.length; i++) {
                    assignees[i] = new AssignmentDest.VarDest(cs.pushVar());
                }
                bb.effects.add(new Effect.AssignManyStmt(assignees, call));
            }
        });
    }

    static {
        CONVERTERS.put(REF_NULL, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new Effect.AssignmentStmt(
                new Expr.ConstExpr(null),
                new AssignmentDest.VarDest(cs.pushVar())
        )));
        CONVERTERS.put(REF_IS_NULL, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new Effect.AssignmentStmt(
                new Expr.IsNullExpr(new Expr.VarExpr(cs.popVar())),
                new AssignmentDest.VarDest(cs.pushVar())
        )));
        CONVERTERS.put(REF_FUNC, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new Effect.AssignmentStmt(
                new Expr.FuncRefExpr(((FuncRefInsnNode) node).function),
                new AssignmentDest.VarDest(cs.pushVar())
        )));
    }

    static {
        CONVERTERS.put(DROP, (cs, node) -> cs.popV());
        CONVERTERS.put(new byte[]{
                SELECT,
                SELECTT
        }, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new Effect.AssignmentStmt(
                new Expr.SelectExpr(
                        new Expr.VarExpr(cs.popVar()), // cond
                        new Expr.VarExpr(cs.popVar()),
                        new Expr.VarExpr(cs.popVar())
                ),
                new AssignmentDest.VarDest(cs.pushVar())
        )));
    }

    static {
        CONVERTERS.put(LOCAL_GET, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new Effect.AssignmentStmt(
                new Expr.VarExpr(cs.refVar(((VariableInsnNode) node).variable)),
                new AssignmentDest.VarDest(cs.pushVar())
        )));
        CONVERTERS.put(new byte[]{
                LOCAL_SET,
                LOCAL_TEE
        }, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new Effect.AssignmentStmt(
                new Expr.VarExpr(cs.refVar(node.opcode == LOCAL_SET ? cs.popV() : cs.vals)),
                new AssignmentDest.VarDest(cs.refVar(((VariableInsnNode) node).variable))
        )));
        CONVERTERS.put(GLOBAL_GET, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new Effect.AssignmentStmt(
                new Expr.GlobalRefExpr(((VariableInsnNode) node).variable),
                new AssignmentDest.VarDest(cs.pushVar())
        )));
        CONVERTERS.put(GLOBAL_SET, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new Effect.AssignmentStmt(
                new Expr.VarExpr(cs.popVar()),
                new AssignmentDest.GlobalDest(((VariableInsnNode) node).variable)
        )));
    }

    static {
        CONVERTERS.put(TABLE_GET, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new Effect.AssignmentStmt(
                new Expr.TableRefExpr(((TableInsnNode) node).table, new Expr.VarExpr(cs.popVar())),
                new AssignmentDest.VarDest(cs.pushVar())
        )));
        CONVERTERS.put(TABLE_SET, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new Effect.AssignmentStmt(
                new Expr.VarExpr(cs.popVar()),
                new AssignmentDest.TableDest(((TableInsnNode) node).table,
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
        return (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new Effect.AssignmentStmt(
                new Expr.DerefExpr(
                        new Expr.VarExpr(cs.popVar()),
                        outType,
                        bytesRead,
                        extUnsigned
                ),
                new AssignmentDest.VarDest(cs.pushVar())
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
        return (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new Effect.AssignmentStmt(
                new Expr.VarExpr(cs.popVar()),
                new AssignmentDest.MemoryDest(new Expr.VarExpr(cs.popVar()), bytesWritten)
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
        CONVERTERS.put(MEMORY_SIZE, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new Effect.AssignmentStmt(
                new Expr.MemorySizeExpr(),
                new AssignmentDest.VarDest(cs.pushVar())
        )));
        CONVERTERS.put(MEMORY_GROW, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new Effect.AssignmentStmt(
                new Expr.MemoryGrowExpr(new Expr.VarExpr(cs.popVar())),
                new AssignmentDest.VarDest(cs.pushVar())
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
        }, (cs, node) -> cs.ctrlsRef(0).bb.effects.add(new Effect.AssignmentStmt(
                new Expr.ConstExpr(((ConstInsnNode) node).value),
                new AssignmentDest.VarDest(cs.pushVar())
        )));
    }

    public static Converter makeOpInsn(int arity) {
        return (cs, node) -> {
            cs.popVs(arity);
            Expr[] args = new Expr[arity];
            for (int i = 0; i < args.length; i++) {
                args[i] = new Expr.VarExpr(cs.refVar(cs.vals + i + 1));
            }
            cs.ctrlsRef(0).bb.effects.add(new Effect.AssignmentStmt(
                    new Expr.OperatorExpr(
                            node.opcode,
                            node instanceof PrefixInsnNode ? ((PrefixInsnNode) node).intOpcode : 0,
                            args
                    ),
                    new AssignmentDest.VarDest(cs.pushVar())
            ));
        };
    }

    static {
        // comparisons
        {
            CONVERTERS.put(I32_EQZ, makeOpInsn(1));
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
            }, makeOpInsn(2));

            CONVERTERS.put(I64_EQZ, makeOpInsn(1));
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
            }, makeOpInsn(2));

            CONVERTERS.put(new byte[]{
                    F32_EQ,
                    F32_NE,
                    F32_LT,
                    F32_GT,
                    F32_LE,
                    F32_GE,
            }, makeOpInsn(2));

            CONVERTERS.put(new byte[]{
                    F64_EQ,
                    F64_NE,
                    F64_LT,
                    F64_GT,
                    F64_LE,
                    F64_GE,
            }, makeOpInsn(2));
        }

        // maths
        {
            CONVERTERS.put(new byte[]{
                    I32_CLZ,
                    I32_CTZ,
                    I32_POPCNT,
            }, makeOpInsn(1));
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
            }, makeOpInsn(2));

            CONVERTERS.put(new byte[]{
                    I64_CLZ,
                    I64_CTZ,
                    I64_POPCNT,
            }, makeOpInsn(1));
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
            }, makeOpInsn(2));

            CONVERTERS.put(new byte[]{
                    F32_ABS,
                    F32_NEG,
                    F32_CEIL,
                    F32_FLOOR,
                    F32_TRUNC,
                    F32_NEAREST,
                    F32_SQRT,
            }, makeOpInsn(1));
            CONVERTERS.put(new byte[]{
                    F32_ADD,
                    F32_SUB,
                    F32_MUL,
                    F32_DIV,
                    F32_MIN,
                    F32_MAX,
                    F32_COPYSIGN,
            }, makeOpInsn(2));

            CONVERTERS.put(new byte[]{
                    F64_ABS,
                    F64_NEG,
                    F64_CEIL,
                    F64_FLOOR,
                    F64_TRUNC,
                    F64_NEAREST,
                    F64_SQRT,
            }, makeOpInsn(1));
            CONVERTERS.put(new byte[]{
                    F64_ADD,
                    F64_SUB,
                    F64_MUL,
                    F64_DIV,
                    F64_MIN,
                    F64_MAX,
                    F64_COPYSIGN,
            }, makeOpInsn(2));
        }

        // conversions
        {
            CONVERTERS.put(new byte[]{
                    I32_WRAP_I64,
                    I32_TRUNC_F32_S,
                    I32_TRUNC_F32_U,
                    I32_TRUNC_F64_S,
                    I32_TRUNC_F64_U,
                    I64_EXTEND_I32_S,
                    I64_EXTEND_I32_U,
                    I64_TRUNC_F32_S,
                    I64_TRUNC_F32_U,
                    I64_TRUNC_F64_S,
                    I64_TRUNC_F64_U,
                    F32_CONVERT_I32_S,
                    F32_CONVERT_I32_U,
                    F32_CONVERT_I64_S,
                    F32_CONVERT_I64_U,
                    F32_DEMOTE_F64,
                    F64_CONVERT_I32_S,
                    F64_CONVERT_I32_U,
                    F64_CONVERT_I64_S,
                    F64_CONVERT_I64_U,
                    F64_PROMOTE_F32,
                    I32_REINTERPRET_F32,
                    I64_REINTERPRET_F64,
                    F32_REINTERPRET_I32,
                    F64_REINTERPRET_I64,
            }, makeOpInsn(1));
        }

        // extension
        {
            CONVERTERS.put(new byte[]{
                    I32_EXTEND8_S,
                    I32_EXTEND16_S,
                    I64_EXTEND8_S,
                    I64_EXTEND16_S,
                    I64_EXTEND32_S,
            }, makeOpInsn(1));
        }

        // saturating truncation
        {
            CONVERTERS.putInt(new int[]{
                    I32_TRUNC_SAT_F32_S,
                    I32_TRUNC_SAT_F32_U,
                    I32_TRUNC_SAT_F64_S,
                    I32_TRUNC_SAT_F64_U,
                    I64_TRUNC_SAT_F32_S,
                    I64_TRUNC_SAT_F32_U,
                    I64_TRUNC_SAT_F64_S,
                    I64_TRUNC_SAT_F64_U,
            }, makeOpInsn(1));
        }
    }

    public static Function convert(
            TypeNode[] funcTypes,
            FuncNode[] referencableFuncs,
            int argC,
            int localsC,
            int returns,
            ExprNode expr
    ) {
        ConvertState state = new ConvertState(funcTypes, referencableFuncs, argC + localsC, returns);
        state.pushC(END, new TypeNode(new byte[0], new byte[returns]));
        BasicBlock firstBb = state.ctrlsRef(0).bb;
        for (int i = 0; i < argC; i++) {
            Var argVar = new Var("arg" + i);
            state.varVals.add(argVar);
            firstBb.effects.add(new Effect.AssignmentStmt(
                    new Expr.FuncArgExpr(i),
                    new AssignmentDest.VarDest(argVar)
            ));
        }
        for (int i = 0; i < localsC; ++i) {
            Var localVar = new Var("local" + i);
            state.varVals.add(localVar);
            firstBb.effects.add(new Effect.AssignmentStmt(
                    new Expr.ZeroInitExpr(),
                    new AssignmentDest.VarDest(localVar)
            ));
        }
        for (AbstractInsnNode insn : expr) {
            Converter converter = CONVERTERS.get(insn);
            if (converter == null) {
                throw new UnsupportedOperationException("Instruction not supported");
            }
            converter.convert(state, insn);
        }
        Function func = state.func;
        opt0(func);
        computeDoms(func);
        computeDomFrontier(func);
        assignVariables(func);
        return func;
    }

    public static void opt0(Function func) {
        // primitive optimisations that can be done even before we are in SSA,
        // and which reduce the size of the graph significantly
        // also sorts the list into pre-walk order

        for (BasicBlock block : func.blocks) {
            collapseJumpTargets(block);
        }

        Set<BasicBlock> alive = new LinkedHashSet<>();
        {
            List<BasicBlock> queue = new ArrayList<>();
            BasicBlock root = func.blocks.get(0);
            queue.add(root);
            alive.add(root);
            while (!queue.isEmpty()) {
                BasicBlock next = queue.remove(queue.size() - 1);
                if (next.control instanceof Control.Break) {
                    Control.Break br = (Control.Break) next.control;
                    for (BasicBlock target : br.targets) {
                        if (alive.add(target)) queue.add(target);
                    }
                    if (alive.add(br.dfltTarget)) queue.add(br.dfltTarget);
                }
            }
        }
        func.blocks = new ArrayList<>(alive);
    }

    public static BasicBlock maybeCollapseTarget(BasicBlock target) {
        if (target.effects.isEmpty()) {
            if (target.control instanceof Control.Break) {
                Control.Break targetControl = (Control.Break) target.control;
                if (targetControl.targets.length == 0) {
                    if (targetControl.dfltTarget != target) {
                        return targetControl.dfltTarget = maybeCollapseTarget(targetControl.dfltTarget);
                    }
                }
            }
        }
        return target;
    }

    public static void collapseJumpTargets(BasicBlock bb) {
        if (bb.control instanceof Control.Break) {
            Control.Break br = (Control.Break) bb.control;
            BasicBlock[] targets = br.targets;
            for (int i = 0; i < targets.length; i++) {
                targets[i] = maybeCollapseTarget(targets[i]);
            }
            br.dfltTarget = maybeCollapseTarget(br.dfltTarget);
        }
    }

    public static void computeDoms(Function func) {
        /*
         Thomas Lengauer and Robert Endre Tarjan. A fast algorithm for finding dominators in a flow-graph.
         ACM Transactions on Programming Languages and Systems, 1(1):121-141, July 1979.
        */

        class S {
            int n = func.blocks.size();
            final int[][] succ = new int[n + 1][];
            final int[] dom = new int[n + 1];
            final int[] parent = new int[n + 1];
            final int[] ancestor = new int[n + 1];
            final int[] child = new int[n + 1];
            final int[] vertex = new int[n + 1];
            final int[] label = new int[n + 1];
            final int[] semi = new int[n + 1];
            final int[] size = new int[n + 1];
            @SuppressWarnings("unchecked")
            final
            Set<Integer>[] pred = new Set[n + 1];
            @SuppressWarnings("unchecked")
            final
            Set<Integer>[] bucket = new Set[n + 1];

            void dfs(int v) {
                semi[v] = ++n;
                vertex[n] = label[v] = v;
                ancestor[v] = child[v] = 0;
                size[v] = 1;
                for (int w : succ[v]) {
                    if (semi[w] == 0) {
                        parent[w] = v;
                        dfs(w);
                    }
                    pred[w].add(v);
                }
            }

            void compress(int v) {
                if (ancestor[ancestor[v]] != 0) {
                    compress(ancestor[v]);
                    if (semi[label[ancestor[v]]] < semi[label[v]]) {
                        label[v] = label[ancestor[v]];
                    }
                    ancestor[v] = ancestor[ancestor[v]];
                }
            }

            int eval(int v) {
                if (ancestor[v] == 0) {
                    return label[v];
                } else {
                    compress(v);
                    return semi[label[ancestor[v]]] >= semi[label[v]]
                            ? label[v]
                            : label[ancestor[v]];
                }
            }

            void link(int v, int w) {
                int s = w;
                while (semi[label[w]] < semi[label[child[s]]]) {
                    if (size[s] + size[child[child[s]]] >= 2 * size[child[s]]) {
                        ancestor[child[s]] = s;
                        child[s] = child[child[s]];
                    } else {
                        size[child[s]] = size[s];
                        s = ancestor[s] = child[s];
                    }
                }
                label[s] = label[w];
                size[v] += size[w];
                if (size[v] < 2 * size[w]) {
                    int t = s;
                    s = child[v];
                    child[v] = t;
                }
                while (s != 0) {
                    ancestor[s] = v;
                    s = child[s];
                }
            }

            void run() {
                int u, w;
                for (int i = 0; i < n; i++) {
                    BasicBlock block = func.blocks.get(i);
                    block.data = i + 1;
                }
                for (BasicBlock block : func.blocks) {
                    int i = (int) block.data;
                    if (block.control instanceof Control.Break) {
                        Control.Break br = (Control.Break) block.control;
                        succ[i] = new int[br.targets.length + 1];
                        succ[i][0] = ((int) br.dfltTarget.data);
                        for (int j = 1; j < succ[i].length; j++) {
                            succ[i][j] = ((int) br.targets[j - 1].data);
                        }
                    } else {
                        succ[i] = new int[0];
                    }
                }

                for (int v = 1; v <= n; ++v) {
                    pred[v] = new HashSet<>();
                    bucket[v] = new HashSet<>();
                    semi[v] = 0;
                }
                n = 0;
                dfs(1);
                size[0] = label[0] = semi[0] = 0;
                for (int i = n; i >= 2; i--) {
                    w = vertex[i];
                    for (int v : pred[w]) {
                        u = eval(v);
                        if (semi[u] < semi[w]) {
                            semi[w] = semi[u];
                        }
                    }
                    bucket[vertex[semi[w]]].add(w);
                    link(parent[w], w);
                    for (int v : bucket[parent[w]]) {
                        u = eval(v);
                        dom[v] = semi[u] < semi[v] ? u : parent[w];
                    }
                    bucket[parent[w]].clear();
                }
                for (int i = 2; i <= n; ++i) {
                    w = vertex[i];
                    if (dom[w] != vertex[semi[w]]) {
                        dom[w] = dom[dom[w]];
                    }
                }
                dom[1] = 0;

                for (int i = 1; i < func.blocks.size(); i++) {
                    BasicBlock blockI = func.blocks.get(i);
                    blockI.idom = func.blocks.get(dom[i + 1] - 1);
                    blockI.preds = new ArrayList<>();
                    for (int p : pred[i + 1]) {
                        blockI.preds.add(func.blocks.get(p - 1));
                    }
                }
                func.blocks.get(0).preds = new ArrayList<>();
            }
        }
        new S().run();
    }

    public static void computeDomFrontier(Function func) {
        // Cooper, Keith D.; Harvey, Timothy J.; Kennedy, Ken (2001). "A Simple, Fast Dominance Algorithm"
        for (BasicBlock block : func.blocks) {
            block.domFrontier = new HashSet<>();
        }
        for (BasicBlock block : func.blocks) {
            if (block.preds.size() >= 2) {
                for (BasicBlock pred : block.preds) {
                    BasicBlock runner = pred;
                    while (runner != block.idom) {
                        runner.domFrontier.add(block);
                        runner = runner.idom;
                    }
                }
            }
        }
    }

    public static void assignVariables(Function func) {
        class BlockData {
            final Set<Var>
                    gen = new HashSet<>(),
                    kill = new HashSet<>(),
                    liveIn = new LinkedHashSet<>(),
                    liveOut = new HashSet<>();
            final Set<BasicBlock> succ = new HashSet<>();
            final Set<BasicBlock> idominates = new LinkedHashSet<>();

            final Map<Var, Effect.AssignmentStmt> phis = new LinkedHashMap<>();
        }
        for (BasicBlock block : func.blocks) {
            BlockData data = new BlockData();
            block.data = data;
            Set<Var> used = data.gen;
            Set<Var> assigned = data.kill;

            List<Expr> stack = new ArrayList<>();
            class S {
                void walkChildren() {
                    while (!stack.isEmpty()) {
                        Expr next = stack.remove(stack.size() - 1);
                        if (next instanceof Expr.VarExpr) {
                            Var var = ((Expr.VarExpr) next).var;
                            if (!assigned.contains(var)) {
                                used.add(var);
                            }
                        } else {
                            for (Expr child : next.children()) {
                                stack.add(child);
                            }
                        }
                    }
                }
            }

            S s = new S();
            for (Effect effect : block.effects) {
                stack.add(effect.expr());
                s.walkChildren();
                for (AssignmentDest dest : effect.dests()) {
                    if (dest instanceof AssignmentDest.VarDest) {
                        assigned.add(((AssignmentDest.VarDest) dest).var);
                    } else {
                        stack.addAll(dest.exprs());
                    }
                }
                s.walkChildren();
            }
            for (Expr child : block.control.exprs()) {
                stack.add(child);
            }
            s.walkChildren();

            if (block.control instanceof Control.Break) {
                Control.Break br = (Control.Break) block.control;
                data.succ.add(br.dfltTarget);
                data.succ.addAll(Arrays.asList(br.targets));
            }

            data.liveIn.addAll(data.gen);
        }

        Set<BasicBlock> workQueue = new LinkedHashSet<>();
        for (int i = func.blocks.size() - 1; i >= 0; i--) {
            workQueue.add(func.blocks.get(i));
        }
        while (!workQueue.isEmpty()) {
            Iterator<BasicBlock> iterator = workQueue.iterator();
            BasicBlock next = iterator.next();
            iterator.remove();
            BlockData data = (BlockData) next.data;
            boolean changed = false;
            for (BasicBlock succ : data.succ) {
                for (Var varIn : ((BlockData) succ.data).liveIn) {
                    if (data.liveOut.add(varIn)) {
                        if (!data.kill.contains(varIn)) {
                            changed = true;
                            data.liveIn.add(varIn);
                        }
                    }
                }
            }
            if (changed) {
                workQueue.addAll(next.preds);
            }
        }

        Iterator<BasicBlock> iter = func.blocks.iterator();
        iter.next();
        while (iter.hasNext()) {
            BasicBlock block = iter.next();
            ((BlockData) block.idom.data).idominates.add(block);
        }

        Set<Var> globals = new LinkedHashSet<>();
        Map<Var, Set<BasicBlock>> varKilledIn = new HashMap<>();
        for (BasicBlock block : func.blocks) {
            BlockData data = (BlockData) block.data;
            for (Var killed : data.kill) {
                varKilledIn.computeIfAbsent(killed, $ -> new HashSet<>()).add(block);
                globals.addAll(data.liveOut);
            }
        }

        for (Var global : globals) {
            List<BasicBlock> workList = new ArrayList<>(varKilledIn.getOrDefault(global, Collections.emptySet()));
            while (!workList.isEmpty()) {
                BasicBlock next = workList.remove(workList.size() - 1);
                for (BasicBlock fBlock : next.domFrontier) {
                    BlockData data = (BlockData) fBlock.data;
                    if (data.liveIn.contains(global)) {
                        Map<Var, Effect.AssignmentStmt> phis = data.phis;
                        if (!phis.containsKey(global)) {
                            phis.put(global, new Effect.AssignmentStmt(
                                    new Expr.PhiExpr(new LinkedHashMap<>()),
                                    new AssignmentDest.VarDest(global)
                            ));
                            workList.add(fBlock);
                        }
                    }
                }
            }
        }

        for (BasicBlock block : func.blocks) {
            block.effects.addAll(0, ((BlockData) block.data).phis.values());
        }

        class Renamer {
            final Map<Var, List<Var>> varStacks = new HashMap<>();

            void replaceUsages(Expr expr) {
                if (expr instanceof Expr.VarExpr) {
                    Expr.VarExpr varExpr = (Expr.VarExpr) expr;
                    varExpr.var = top(varExpr.var);
                } else if (!(expr instanceof Expr.PhiExpr)) {
                    for (Expr child : expr.children()) {
                        replaceUsages(child);
                    }
                }
            }

            Var top(Var var) {
                List<Var> stack = varStacks.get(var);
                if (stack != null && !stack.isEmpty()) {
                    return stack.get(stack.size() - 1);
                }
                return var;
            }

            void dfs(BasicBlock block) {
                Map<Var, Integer> varsReplaced = new HashMap<>();
                for (Effect effect : block.effects) {
                    replaceUsages(effect.expr());
                    for (AssignmentDest dest : effect.dests()) {
                        if (dest instanceof AssignmentDest.VarDest) {
                            AssignmentDest.VarDest varDest = (AssignmentDest.VarDest) dest;
                            Var newVar = func.newVar(varDest.var.name);
                            List<Var> varStack = varStacks.computeIfAbsent(varDest.var, $ -> new ArrayList<>());
                            varsReplaced.put(varDest.var, varStack.size());
                            varStack.add(newVar);
                            varDest.var = newVar;
                        } else {
                            for (Expr expr : dest.exprs()) {
                                replaceUsages(expr);
                            }
                        }
                    }
                }
                for (Expr expr : block.control.exprs()) {
                    replaceUsages(expr);
                }

                BlockData data = (BlockData) block.data;
                for (BasicBlock succ : data.succ) {
                    BlockData bData = (BlockData) succ.data;
                    for (Map.Entry<Var, Effect.AssignmentStmt> entry : bData.phis.entrySet()) {
                        ((Expr.PhiExpr) entry.getValue().value)
                                .branches
                                .put(block, new Expr.VarExpr(top(entry.getKey())));
                    }
                }

                for (BasicBlock next : data.idominates) {
                    dfs(next);
                }
                for (Map.Entry<Var, Integer> entry : varsReplaced.entrySet()) {
                    Var origVar = entry.getKey();
                    Integer stackSize = entry.getValue();
                    List<Var> stack = varStacks.get(origVar);
                    stack.subList(stackSize + 1, stack.size()).clear();
                }
            }

            void run() {
                dfs(func.blocks.get(0));
            }
        }
        new Renamer().run();

        for (BasicBlock block : func.blocks) {
            block.data = null;
        }
    }
}

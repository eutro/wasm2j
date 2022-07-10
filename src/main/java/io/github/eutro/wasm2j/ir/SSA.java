package io.github.eutro.wasm2j.ir;

import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SSA {
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
                for (BasicBlock target : next.control.targets()) {
                    if (alive.add(target)) queue.add(target);
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

            for (BasicBlock basicBlock : block.control.targets()) data.succ.add(basicBlock);
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
                throw new IllegalStateException("variable used before definition");
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
                            varsReplaced.putIfAbsent(varDest.var, varStack.size());
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
                    stack.subList(stackSize, stack.size()).clear();
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

    public static class Var {
        public String name;

        public Object type;

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
            sb.append(toTargetString()).append("\n{\n");
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

        public abstract Iterable<BasicBlock> targets();

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

            @Override
            public Iterable<BasicBlock> targets() {
                return () -> Stream.concat(Stream.of(dfltTarget), Arrays.stream(targets)).iterator();
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

            @Override
            public Iterable<BasicBlock> targets() {
                return Collections.emptyList();
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

            @Override
            public Iterable<BasicBlock> targets() {
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
}

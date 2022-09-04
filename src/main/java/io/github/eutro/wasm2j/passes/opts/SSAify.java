package io.github.eutro.wasm2j.passes.opts;

import io.github.eutro.wasm2j.ext.CommonExts;
import io.github.eutro.wasm2j.ext.Ext;
import io.github.eutro.wasm2j.ops.UnaryOpKey;
import io.github.eutro.wasm2j.passes.InPlaceIrPass;
import io.github.eutro.wasm2j.passes.meta.ComputeDomFrontier;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.ops.CommonOps;

import java.util.*;

public class SSAify implements InPlaceIrPass<Function> {
    public static final SSAify INSTANCE = new SSAify();

    @Override
    public void runInPlace(Function function) {
        assignVariables(function);
    }

    private static void assignVariables(Function func) {
        for (BasicBlock block : func.blocks) {
            if (!block.getExt(CommonExts.DOM_FRONTIER).isPresent()) {
                ComputeDomFrontier.INSTANCE.runInPlace(func);
                break;
            }
        }

        class BlockData {
            final Set<Var>
                    gen = new HashSet<>(),
                    kill = new HashSet<>(),
                    liveIn = new LinkedHashSet<>(),
                    liveOut = new HashSet<>();
            final Set<BasicBlock> succ = new HashSet<>();
            final Set<BasicBlock> idominates = new LinkedHashSet<>();

            final Map<Var, Effect> phis = new LinkedHashMap<>();
        }
        Ext<BlockData> bdExt = new Ext<>(BlockData.class);
        for (BasicBlock block : func.blocks) {
            BlockData data = new BlockData();
            block.attachExt(bdExt, data);
            Set<Var> used = data.gen;
            Set<Var> assigned = data.kill;

            for (Effect effect : block.getEffects()) {
                for (Var arg : effect.insn().args) {
                    if (!assigned.contains(arg)) used.add(arg);
                }
                assigned.addAll(effect.getAssignsTo());
            }
            for (Var arg : block.getControl().insn.args) {
                if (!assigned.contains(arg)) used.add(arg);
            }

            data.succ.addAll(block.getControl().targets);
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
            BlockData data = next.getExtOrThrow(bdExt);
            boolean changed = false;
            for (BasicBlock succ : data.succ) {
                BlockData succData = succ.getExtOrThrow(bdExt);
                for (Var varIn : succData.liveIn) {
                    if (data.liveOut.add(varIn)) {
                        if (!data.kill.contains(varIn)) {
                            changed = true;
                            data.liveIn.add(varIn);
                        }
                    }
                }
            }
            if (changed) {
                workQueue.addAll(next.getExtOrThrow(CommonExts.PREDS));
            }
        }

        Iterator<BasicBlock> iter = func.blocks.iterator();
        iter.next();
        while (iter.hasNext()) {
            BasicBlock block = iter.next();
            BlockData idomData = block.getExtOrThrow(CommonExts.IDOM).getExtOrThrow(bdExt);
            idomData.idominates.add(block);
        }

        Set<Var> globals = new LinkedHashSet<>();
        Map<Var, Set<BasicBlock>> varKilledIn = new HashMap<>();
        for (BasicBlock block : func.blocks) {
            BlockData data = block.getExtOrThrow(bdExt);
            for (Var killed : data.kill) {
                varKilledIn.computeIfAbsent(killed, $ -> new HashSet<>()).add(block);
                globals.addAll(data.liveOut);
            }
        }

        for (Var global : globals) {
            List<BasicBlock> workList = new ArrayList<>(varKilledIn.getOrDefault(global, Collections.emptySet()));
            while (!workList.isEmpty()) {
                BasicBlock next = workList.remove(workList.size() - 1);
                for (BasicBlock fBlock : next.getExtOrThrow(CommonExts.DOM_FRONTIER)) {
                    BlockData data = fBlock.getExtOrThrow(bdExt);
                    if (data.liveIn.contains(global)) {
                        Map<Var, Effect> phis = data.phis;
                        if (!phis.containsKey(global)) {
                            Effect phiEffect = CommonOps.PHI.create(new ArrayList<>()).insn().assignTo(global);
                            phis.put(global, phiEffect);
                            workList.add(fBlock);
                        }
                    }
                }
            }
        }

        for (BasicBlock block : func.blocks) {
            BlockData data = block.getExtOrThrow(bdExt);
            block.getEffects().addAll(0, data.phis.values());
        }

        class Renamer {
            final Map<Var, List<Var>> varStacks = new HashMap<>();

            void replaceUsages(Insn insn) {
                if (insn.op.key == CommonOps.PHI) return;
                ListIterator<Var> iter = insn.args.listIterator();
                while (iter.hasNext()) {
                    iter.set(top(iter.next()));
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
                for (Effect effect : block.getEffects()) {
                    replaceUsages(effect.insn());
                    ListIterator<Var> it = effect.getAssignsTo().listIterator();
                    while (it.hasNext()) {
                        Var dest = it.next();
                        Var newVar = func.newVar(dest.name);
                        List<Var> varStack = varStacks.computeIfAbsent(dest, $ -> new ArrayList<>());
                        varsReplaced.putIfAbsent(dest, varStack.size());
                        varStack.add(newVar);
                        it.set(newVar);
                    }
                }
                replaceUsages(block.getControl().insn);

                BlockData data = block.getExtOrThrow(bdExt);
                for (BasicBlock succ : data.succ) {
                    BlockData succData = succ.getExtOrThrow(bdExt);
                    for (Map.Entry<Var, Effect> entry : succData.phis.entrySet()) {
                        Insn phiInsn = entry.getValue().insn();
                        UnaryOpKey<List<BasicBlock>>.UnaryOp phi
                                = CommonOps.PHI.check(phiInsn.op).orElseThrow(RuntimeException::new);
                        Var branchVal = top(entry.getKey());
                        phi.arg.add(block);
                        phiInsn.args.add(branchVal);
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
            block.removeExt(bdExt);
        }
    }
}

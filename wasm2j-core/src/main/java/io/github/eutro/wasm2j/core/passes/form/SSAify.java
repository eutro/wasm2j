package io.github.eutro.wasm2j.core.passes.form;

import io.github.eutro.wasm2j.core.ext.CommonExts;
import io.github.eutro.wasm2j.core.ext.CommonExts.LiveData;
import io.github.eutro.wasm2j.core.ext.Ext;
import io.github.eutro.wasm2j.core.ext.MetadataState;
import io.github.eutro.wasm2j.core.ops.CommonOps;
import io.github.eutro.wasm2j.core.ops.UnaryOpKey;
import io.github.eutro.wasm2j.core.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.core.ssa.*;

import java.util.*;

public class SSAify implements InPlaceIRPass<Function> {
    public static final SSAify INSTANCE = new SSAify();

    @Override
    public void runInPlace(Function function) {
        assignVariables(function);
    }

    private static void assignVariables(Function func) {
        MetadataState ms = func.getExtOrThrow(CommonExts.METADATA_STATE);
        ms.ensureValid(func, MetadataState.DOM_FRONTIER, MetadataState.LIVE_DATA);

        func.clearVarNames();

        class BlockData {
            final Set<BasicBlock> idominates = new LinkedHashSet<>();

            final Map<Var, Effect> phis = new LinkedHashMap<>();
        }
        Ext<BlockData> bdExt = Ext.create(BlockData.class, "blockData");
        for (BasicBlock block : func.blocks) {
            block.attachExt(bdExt, new BlockData());
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
            LiveData data = block.getExtOrThrow(CommonExts.LIVE_DATA);
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
                    LiveData liveData = fBlock.getExtOrThrow(CommonExts.LIVE_DATA);
                    if (liveData.liveIn.contains(global)) {
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
                ListIterator<Var> iter = insn.args().listIterator();
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
                replaceUsages(block.getControl().insn());

                BlockData data = block.getExtOrThrow(bdExt);
                for (BasicBlock succ : block.getControl().targets) {
                    BlockData succData = succ.getExtOrThrow(bdExt);
                    for (Map.Entry<Var, Effect> entry : succData.phis.entrySet()) {
                        Insn phiInsn = entry.getValue().insn();
                        UnaryOpKey<List<BasicBlock>>.UnaryOp phi
                                = CommonOps.PHI.check(phiInsn.op).orElseThrow(RuntimeException::new);
                        Var branchVal = top(entry.getKey());
                        phi.arg.add(block);
                        phiInsn.args().add(branchVal);
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
            // we've renamed all the variables, none of it is valid anymore
            block.removeExt(CommonExts.LIVE_DATA);
        }

        ms.varsChanged();
        ms.validate(MetadataState.SSA_FORM);
    }
}

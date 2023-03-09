package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.Op;

import java.util.*;

public class Inliner {
    private final HashMap<Var, Var> varMap = new HashMap<>();
    private final HashMap<BasicBlock, BasicBlock> blockMap = new HashMap<>();
    private final Map<BasicBlock, Var[]> returnVars = new HashMap<>();
    private final IRBuilder ib;

    public Inliner(IRBuilder ib) {
        this.ib = ib;
    }

    public Insn inline(
            Function func,
            List<Var> args
    ) {
        BasicBlock startBlock;
        boolean blocksInlined = func.blocks.size() == 1
                && func.blocks.get(0).getControl().insn().op == CommonOps.RETURN;
        BasicBlock targetBlock = null;
        if (blocksInlined) {
            startBlock = ib.getBlock();
        } else {
            startBlock = ib.func.newBb();
            ib.insertCtrl(Control.br(startBlock));
            targetBlock = ib.func.newBb();
        }
        blockMap.put(func.blocks.get(0), startBlock);
        for (BasicBlock thisBb : func.blocks) {
            BasicBlock iBb = blockMap.computeIfAbsent(thisBb, $ -> ib.func.newBb());
            ib.setBlock(iBb);
            for (Effect effect : thisBb.getEffects()) {
                Op op = effect.insn().op;
                Integer argIdx = CommonOps.ARG.check(op).map(it -> it.arg).orElse(null);
                Insn insn;
                if (argIdx != null) {
                    insn = CommonOps.IDENTITY.insn(args.get(argIdx));
                } else if (op.key == CommonOps.PHI) {
                    insn = CommonOps.PHI.create(new ArrayList<>(
                                    Arrays.asList(refreshBbs(CommonOps.PHI.cast(op).arg))))
                            .insn(refreshVars(effect.insn().args));
                } else {
                    insn = op.insn(refreshVars(effect.insn().args));
                }
                ib.insert(insn.assignTo(refreshVars(effect.getAssignsTo())));
            }
            Control ctrl = thisBb.getControl();
            if (ctrl.insn().op.key == CommonOps.RETURN.key) {
                returnVars.put(iBb, refreshVars(ctrl.insn().args));
                if (!blocksInlined) {
                    ib.insertCtrl(Control.br(targetBlock));
                }
            } else {
                ib.insertCtrl(ctrl.insn().op
                        .insn(refreshVars(ctrl.insn().args))
                        .jumpsTo(refreshBbs(ctrl.targets)));
            }
        }
        if (!blocksInlined) {
            ib.setBlock(targetBlock);
        }

        if (returnVars.isEmpty()) {
            return CommonOps.TRAP.create("unreachable reached").insn();
        }

        int expectedLength = -1;
        List<Var> returns;
        if (returnVars.size() == 1) {
            returns = new ArrayList<>(Arrays.asList(returnVars.values().iterator().next()));
        } else {
            assert !blocksInlined;
            List<List<BasicBlock>> phiSources = new ArrayList<>();
            List<List<Var>> phiVars = new ArrayList<>();
            for (Map.Entry<BasicBlock, Var[]> entry : returnVars.entrySet()) {
                if (expectedLength == -1) {
                    expectedLength = entry.getValue().length;
                    for (int i = 0; i < expectedLength; i++) {
                        phiSources.add(new ArrayList<>());
                        phiVars.add(new ArrayList<>());
                    }
                } else if (expectedLength != entry.getValue().length) {
                    throw new IllegalStateException("function has varying return counts");
                }
                for (List<BasicBlock> phiSource : phiSources) {
                    phiSource.add(entry.getKey());
                }
                int i = 0;
                for (List<Var> phiVar : phiVars) {
                    phiVar.add(entry.getValue()[i++]);
                }
            }
            returns = new ArrayList<>(expectedLength);
            for (int i = 0; i < expectedLength; i++) {
                Var outVar = ib.func.newVar("ret." + i);
                returns.add(outVar);
                ib.insert(CommonOps.PHI
                        .create(phiSources.get(i))
                        .insn(phiVars.get(i))
                        .assignTo(outVar));
            }
        }
        return CommonOps.IDENTITY.insn(returns);
    }

    private BasicBlock[] refreshBbs(List<BasicBlock> targets) {
        BasicBlock[] ret = new BasicBlock[targets.size()];
        int i = 0;
        for (BasicBlock target : targets) {
            ret[i++] = blockMap.computeIfAbsent(target, $ -> ib.func.newBb());
        }
        return ret;
    }

    private Var[] refreshVars(List<Var> vars) {
        Var[] ret = new Var[vars.size()];
        int i = 0;
        for (Var var : vars) {
            ret[i++] = varMap.computeIfAbsent(
                    var,
                    old -> ib.func.newVar(old.name)
            );
        }
        return ret;
    }
}

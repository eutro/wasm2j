package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ops.CommonOps;

import java.util.*;

public class Inliner {
    private final Function from, into;
    private final HashMap<Var, Var> varMap = new HashMap<>();
    private final HashMap<BasicBlock, BasicBlock> blockMap = new HashMap<>();
    private final Map<BasicBlock, Var[]> returnVars = new HashMap<>();

    public Inliner(Function from, Function into) {
        this.from = from;
        this.into = into;
    }

    public Insn inline(
            List<Var> args,
            BasicBlock sourceBlock,
            BasicBlock targetBlock
    ) {
        for (BasicBlock thisBb : from.blocks) {
            BasicBlock iBb = blockMap.computeIfAbsent(thisBb, $ -> into.newBb());
            for (Effect effect : thisBb.getEffects()) {
                Integer argIdx = CommonOps.ARG.check(effect.insn().op).map(it -> it.arg).orElse(null);
                Insn insn;
                if (argIdx != null) {
                    insn = CommonOps.IDENTITY.insn(args.get(argIdx));
                } else {
                    insn = effect.insn().op.insn(refreshVars(effect.insn().args));
                }
                iBb.addEffect(insn.assignTo(refreshVars(effect.getAssignsTo())));
            }
            Control ctrl = thisBb.getControl();
            if (ctrl.insn.op.key == CommonOps.RETURN.key) {
                returnVars.put(iBb, refreshVars(ctrl.insn.args));
                iBb.setControl(Control.br(targetBlock));
            } else {
                iBb.setControl(ctrl.insn.op
                        .insn(refreshVars(ctrl.insn.args))
                        .jumpsTo(refreshBbs(ctrl.targets)));
            }
        }
        sourceBlock.setControl(Control.br(blockMap.get(from.blocks.get(0))));

        if (returnVars.isEmpty()) {
            return CommonOps.UNREACHABLE.insn();
        }

        int expectedLength = -1;
        List<Var> returns;
        if (returnVars.size() == 1) {
            returns = new ArrayList<>(Arrays.asList(returnVars.values().iterator().next()));
        } else {
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
                    throw new IllegalStateException("Function has differing return counts");
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
                Var outVar = into.newVar("ret." + i);
                returns.add(outVar);
                targetBlock.addEffect(CommonOps.PHI
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
            ret[i++] = blockMap.computeIfAbsent(target, $ -> into.newBb());
        }
        return ret;
    }

    private Var[] refreshVars(List<Var> vars) {
        Var[] ret = new Var[vars.size()];
        int i = 0;
        for (Var var : vars) {
            ret[i++] = varMap.computeIfAbsent(
                    var,
                    old -> into.newVar(old.name)
            );
        }
        return ret;
    }
}

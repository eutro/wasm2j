package io.github.eutro.wasm2j.passes.misc;

import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.passes.InPlaceIrPass;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.ssa.Module;

import java.util.AbstractList;
import java.util.ListIterator;

public class ForPass {
    public static Functions liftFunctions(IRPass<Function, Function> pass) {
        return new Functions(pass);
    }

    public static BasicBlocks liftBasicBlocks(IRPass<BasicBlock, BasicBlock> pass) {
        return new BasicBlocks(pass);
    }

    public static Insns liftInsns(IRPass<Insn, Insn> pass) {
        return new Insns(pass);
    }

    public static class Functions extends AbstractForPass<Function, Module> {

        private Functions(IRPass<Function, Function> pass) {
            super(pass);
        }

        @Override
        protected ListIterator<Function> getListIter(Module module) {
            return module.funcions.listIterator();
        }
    }

    public static class BasicBlocks extends AbstractForPass<BasicBlock, Function> {

        private BasicBlocks(IRPass<BasicBlock, BasicBlock> pass) {
            super(pass);
        }

        @Override
        protected ListIterator<BasicBlock> getListIter(Function function) {
            return function.blocks.listIterator();
        }

        public Functions lift() {
            return liftFunctions(this);
        }
    }

    public static class Insns extends AbstractForPass<Insn, BasicBlock> {
        private Insns(IRPass<Insn, Insn> pass) {
            super(pass);
        }

        @Override
        protected ListIterator<Insn> getListIter(BasicBlock basicBlock) {
            int effectsSize = basicBlock.getEffects().size();
            return new AbstractList<Insn>() {
                @Override
                public int size() {
                    return effectsSize + 1;
                }

                @Override
                public Insn get(int index) {
                    if (index == effectsSize) return basicBlock.getControl().insn;
                    return basicBlock.getEffects().get(index).insn();
                }

                @Override
                public Insn set(int index, Insn element) {
                    if (index == effectsSize) {
                        Control control = basicBlock.getControl();
                        Insn old = control.insn;
                        control.insn = element;
                        return old;
                    }
                    Effect effect = basicBlock.getEffects().get(index);
                    Insn old = effect.insn();
                    effect.setInsn(element);
                    return old;
                }
            }.listIterator();
        }

        public BasicBlocks lift() {
            return liftBasicBlocks(this);
        }
    }

    private static abstract class AbstractForPass<T, Lifted> implements InPlaceIrPass<Lifted> {
        private final IRPass<T, T> pass;

        private AbstractForPass(IRPass<T, T> pass) {
            this.pass = pass;
        }

        protected abstract ListIterator<T> getListIter(Lifted lifted);

        @Override
        public void runInPlace(Lifted lifted) {
            ListIterator<T> iter = getListIter(lifted);
            while (iter.hasNext()) {
                try {
                    iter.set(pass.run(iter.next()));
                } catch (RuntimeException e) {
                    throw new RuntimeException("error running pass on element " + iter.previousIndex(), e);
                }
            }
        }
    }
}

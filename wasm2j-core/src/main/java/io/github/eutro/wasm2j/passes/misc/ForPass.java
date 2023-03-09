package io.github.eutro.wasm2j.passes.misc;

import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.*;

import java.util.*;

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

    private interface SetableIterator<E> extends Iterator<E> {
        void set(E el);

        default void finish() {
        }
    }

    private static class LISetableIterator<E> implements SetableIterator<E> {
        private final ListIterator<E> it;

        private LISetableIterator(ListIterator<E> it) {
            this.it = it;
        }

        @Override
        public void set(E el) {
            it.set(el);
        }

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public E next() {
            return it.next();
        }
    }

    public static class Functions extends AbstractForPass<Function, Module> {

        private Functions(IRPass<Function, Function> pass) {
            super(pass);
        }

        @Override
        protected Iterator<Function> getIter(Module module) {
            return module.functions.iterator();
        }

        @Override
        protected SetableIterator<Function> getSetableIter(Module module) {
            List<Function> asList = new ArrayList<>(module.functions);
            return new LISetableIterator<Function>(asList.listIterator()) {
                @Override
                public void finish() {
                    module.functions.clear();
                    module.functions.addAll(asList);
                }
            };
        }
    }

    public static class BasicBlocks extends AbstractForPass<BasicBlock, Function> {

        private BasicBlocks(IRPass<BasicBlock, BasicBlock> pass) {
            super(pass);
        }

        @Override
        protected Iterator<BasicBlock> getIter(Function function) {
            return function.blocks.iterator();
        }

        @Override
        protected SetableIterator<BasicBlock> getSetableIter(Function function) {
            return new LISetableIterator<>(function.blocks.listIterator());
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
        protected SetableIterator<Insn> getSetableIter(BasicBlock basicBlock) {
            int effectsSize = basicBlock.getEffects().size();
            return new LISetableIterator<>(new AbstractList<Insn>() {
                @Override
                public int size() {
                    return effectsSize + 1;
                }

                @Override
                public Insn get(int index) {
                    if (index == effectsSize) return basicBlock.getControl().insn();
                    return basicBlock.getEffects().get(index).insn();
                }

                @Override
                public Insn set(int index, Insn element) {
                    if (index == effectsSize) {
                        Control control = basicBlock.getControl();
                        Insn old = control.insn();
                        control.setInsn(element);
                        return old;
                    }
                    Effect effect = basicBlock.getEffects().get(index);
                    Insn old = effect.insn();
                    effect.setInsn(element);
                    return old;
                }
            }.listIterator());
        }

        public BasicBlocks lift() {
            return liftBasicBlocks(this);
        }
    }

    private static abstract class AbstractForPass<T, Lifted> implements InPlaceIRPass<Lifted> {
        private final IRPass<T, T> pass;

        private AbstractForPass(IRPass<T, T> pass) {
            this.pass = pass;
        }

        protected Iterator<T> getIter(Lifted lifted) {
            return getSetableIter(lifted);
        }

        protected abstract SetableIterator<T> getSetableIter(Lifted lifted);

        @Override
        public void runInPlace(Lifted lifted) {
            int i = 0;
            try {
                if (pass.isInPlace()) {
                    Iterator<T> iter = getIter(lifted);
                    while (iter.hasNext()) {
                        pass.run(iter.next());
                        i++;
                    }
                } else {
                    SetableIterator<T> iter = getSetableIter(lifted);
                    while (iter.hasNext()) {
                        iter.set(pass.run(iter.next()));
                        i++;
                    }
                    iter.finish();
                }
            } catch (Throwable t) {
                t.addSuppressed(new RuntimeException("in element " + i));
                throw t;
            }
        }
    }
}

package io.github.eutro.wasm2j.passes.misc;

import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.passes.InPlaceIRPass;
import io.github.eutro.wasm2j.ssa.*;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.ListIterator;

/**
 * A pass which lifts passes which operate on smaller IR parts into onese that operate on bigger parts.
 */
public class ForPass {
    /**
     * Lift a basic block pass to operate on a full function.
     *
     * @param pass The basic block pass.
     * @return The function pass.
     */
    public static BasicBlocks liftBasicBlocks(IRPass<BasicBlock, BasicBlock> pass) {
        return new BasicBlocks(pass);
    }

    /**
     * Lift an instruction pass to operate on a full basic block.
     *
     * @param pass The instruction pass.
     * @return The basic block pass.
     */
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

    /**
     * A basic block pass lifted to operate on a full function.
     */
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
    }

    /**
     * An instruction pass lifted to operate on a full basic block.
     */
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

        /**
         * Lift this pass to operate on a full function.
         *
         * @return The function pass.
         */
        public BasicBlocks lift() {
            return liftBasicBlocks(this);
        }
    }

    private static abstract class AbstractForPass<T, Lifted> implements InPlaceIRPass<Lifted> {
        private final IRPass<T, T> pass;

        private AbstractForPass(IRPass<T, T> pass) {
            this.pass = pass;
        }

        Iterator<T> getIter(Lifted lifted) {
            return getSetableIter(lifted);
        }

        abstract SetableIterator<T> getSetableIter(Lifted lifted);

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

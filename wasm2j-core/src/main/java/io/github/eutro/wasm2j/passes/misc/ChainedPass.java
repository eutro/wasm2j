package io.github.eutro.wasm2j.passes.misc;

import io.github.eutro.wasm2j.passes.IRPass;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

public class ChainedPass<A, B, C> implements IRPass<A, C> {
    private final IRPass<A, B> firstPass;
    private final IRPass<B, C> nextPass;
    private final boolean isInPlace;

    public ChainedPass(IRPass<A, B> firstPass, IRPass<B, C> nextPass) {
        this.firstPass = firstPass;
        this.nextPass = nextPass;
        isInPlace = firstPass.isInPlace() && nextPass.isInPlace();
    }

    @SuppressWarnings("unchecked")
    private List<IRPass<Object, Object>> listPasses() {
        List<IRPass<?, ?>> passes = new ArrayList<>();
        IRPass<?, ?> pass = this;
        while (pass instanceof ChainedPass) {
            ChainedPass<?, ?, ?> cPass = (ChainedPass<?, ?, ?>) pass;
            passes.add(cPass.nextPass);
            pass = cPass.firstPass;
        }
        passes.add(pass);
        Collections.reverse(passes);
        return (List<IRPass<Object, Object>>) (Object) passes;
    }

    @Override
    public boolean isInPlace() {
        return isInPlace;
    }

    @SuppressWarnings("unchecked")
    @Override
    public C run(A a) {
        ListIterator<IRPass<Object, Object>> li = listPasses().listIterator();
        Object acc = a;
        while (li.hasNext()) {
            try {
                acc = li.next().run(acc);
            } catch (Throwable t) {
                t.addSuppressed(new RuntimeException("running pass " + li.previousIndex() + " in chain"));
                throw t;
            }
        }
        return (C) acc;
    }
}

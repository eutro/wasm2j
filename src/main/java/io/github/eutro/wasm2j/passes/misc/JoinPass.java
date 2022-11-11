package io.github.eutro.wasm2j.passes.misc;

import io.github.eutro.wasm2j.passes.IRPass;

public class JoinPass<S, A, B, R> implements IRPass<S, R> {

    private final IRPass<S, A> lhs;
    private final IRPass<S, B> rhs;
    private final Joiner<A, B, R> joiner;

    public JoinPass(IRPass<S, A> lhs, IRPass<S, B> rhs, Joiner<A, B, R> joiner) {
        this.lhs = lhs;
        this.rhs = rhs;
        this.joiner = joiner;
    }

    public interface Joiner<A, B, R> {
        R join(A a, B b);
    }

    @Override
    public R run(S s) {
        return joiner.join(lhs.run(s), rhs.run(s));
    }
}

package io.github.eutro.wasm2j;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ListIterator;
import java.util.function.Supplier;

class DFA<State, Terminal, Sym> {
    private final State root;
    private final Advancer<State, Sym> advancer;
    private final TerminalGetter<State, Terminal> terminalGetter;

    public DFA(State root,
               Advancer<State, Sym> advancer,
               TerminalGetter<State, Terminal> terminalGetter) {
        this.root = root;
        this.advancer = advancer;
        this.terminalGetter = terminalGetter;
    }

    public Terminal fullMatch(ListIterator<Sym> li, List<Sym> matched) {
        @Nullable Terminal lastTerminal = null;
        int since = 0;
        @NotNull State state = root;
        while (state != null && li.hasNext()) {
            Sym sym = li.next();
            State next = advancer.advance(state, sym);
            if (next == null) {
                li.previous();
                break;
            }
            matched.add(sym);
            state = next;
            Terminal t = terminalGetter.get(state);
            if (t != null) {
                lastTerminal = t;
                since = 0;
            } else {
                ++since;
            }
        }
        for (int i = 0; i < since; i++) {
            li.previous();
        }
        return lastTerminal;
    }

    public interface Advancer<State, Sym> {
        @Nullable State advance(@NotNull State thisState, Sym sym);
    }

    public interface TerminalGetter<State, Terminal> {
        @Nullable Terminal get(@NotNull State thisState);
    }

    public interface NextSetter<State, InSym> {
        void match(@NotNull State thisState, InSym sym, @Nullable State next);
    }

    public interface TerminalSetter<State, Terminal> {
        void set(@NotNull State thisState, @Nullable Terminal terminal);
    }

    public static <State, Terminal, InSym, Sym>
    Builder<State, Terminal, InSym, Sym> builder(Supplier<State> stateSupplier,
                                                 NextSetter<State, InSym> setNext,
                                                 TerminalSetter<State, Terminal> setTerminal,

                                                 Advancer<State, Sym> advancer,
                                                 TerminalGetter<State, Terminal> getTerminal) {
        return new Builder<>(stateSupplier, setNext, setTerminal, advancer, getTerminal);
    }

    public static class Builder<State, Terminal, InSym, Sym> {
        private final State root;
        private final Supplier<State> stateSupplier;
        private final NextSetter<State, InSym> setNext;
        private final TerminalSetter<State, Terminal> setTerminal;
        private final Advancer<State, Sym> advancer;
        private final TerminalGetter<State, Terminal> getTerminal;

        public Builder(Supplier<State> stateSupplier,
                       NextSetter<State, InSym> setNext,
                       TerminalSetter<State, Terminal> setTerminal,
                       Advancer<State, Sym> advancer,
                       TerminalGetter<State, Terminal> getTerminal) {
            root = stateSupplier.get();
            this.stateSupplier = stateSupplier;
            this.setNext = setNext;
            this.setTerminal = setTerminal;
            this.advancer = advancer;
            this.getTerminal = getTerminal;
        }

        public DFA<State, Terminal, Sym> build() {
            return new DFA<>(root, advancer, getTerminal);
        }

        public StateBuilder<Builder<State, Terminal, InSym, Sym>> match(InSym sym) {
            State next = stateSupplier.get();
            setNext.match(root, sym, next);
            return new StateBuilder<>(this, next);
        }

        public class StateBuilder<Result> {
            private final Result res;
            private final State state;

            public StateBuilder(Result res, State state) {
                this.res = res;
                this.state = state;
            }

            public StateBuilder<StateBuilder<Result>> match(InSym sym) {
                State next = stateSupplier.get();
                setNext.match(state, sym, next);
                return new StateBuilder<>(this, next);
            }

            public StateBuilder<Result> repeat(InSym sym) {
                State next = stateSupplier.get();
                setNext.match(state, sym, state);
                return this;
            }

            public <T extends Terminal> Result terminal(T terminal) {
                setTerminal.set(state, terminal);
                return res;
            }

            public Result nonTerminal() {
                return res;
            }
        }
    }
}

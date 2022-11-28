package io.github.eutro.wasm2j.ops;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public class UnaryOpKey<T> extends OpKey {
    private final Function<T, String> printer;
    private boolean allowNull = false;

    public UnaryOpKey(String mnemonic, Function<T, String> printer) {
        super(mnemonic);
        this.printer = printer;
    }

    public UnaryOpKey(String mnemonic) {
        this(mnemonic, Objects::toString);
    }

    public UnaryOpKey<T> allowNull() {
        allowNull = true;
        return this;
    }

    public class UnaryOp extends Op {
        public T arg;

        public UnaryOp(T arg) {
            super(UnaryOpKey.this);
            this.arg = arg;
        }

        @Override
        public String toString() {
            return key + " " + printer.apply(arg);
        }
    }

    public Optional<UnaryOp> check(Op val) {
        if (val.key == this) {
            @SuppressWarnings("unchecked")
            UnaryOp ret = (UnaryOp) val;
            return Optional.of(ret);
        } else {
            return Optional.empty();
        }
    }

    public UnaryOp cast(Op val) {
        return check(val).orElseThrow(ClassCastException::new);
    }

    public UnaryOp create(T arg) {
        if (!allowNull && arg == null) {
            throw new IllegalArgumentException("Argument is null");
        }
        return new UnaryOp(arg);
    }
}

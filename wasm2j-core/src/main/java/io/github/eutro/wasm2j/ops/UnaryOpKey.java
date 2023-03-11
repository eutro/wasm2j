package io.github.eutro.wasm2j.ops;

import org.jetbrains.annotations.Nullable;

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

    public UnaryOp checkNullable(Op val) {
        if (val.key == this) {
            @SuppressWarnings("unchecked")
            UnaryOp ret = (UnaryOp) val;
            return ret;
        } else {
            return null;
        }
    }

    @Nullable
    public T argNullable(Op val) {
        UnaryOp op = checkNullable(val);
        if (op == null) return null;
        return op.arg;
    }

    public Optional<UnaryOp> check(Op val) {
        return Optional.ofNullable(checkNullable(val));
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

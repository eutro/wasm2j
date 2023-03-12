package io.github.eutro.wasm2j.core.ops;

public class SimpleOpKey extends OpKey {
    private final Op op = new Op(this);

    public SimpleOpKey(String mnemonic) {
        super(mnemonic);
    }

    public Op create() {
        return op;
    }
}

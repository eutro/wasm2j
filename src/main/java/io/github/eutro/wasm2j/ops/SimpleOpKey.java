package io.github.eutro.wasm2j.ops;

public class SimpleOpKey extends OpKey {
    public SimpleOpKey(String mnemonic) {
        super(mnemonic);
    }

    public Op create() {
        return new Op(this);
    }
}

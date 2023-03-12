package io.github.eutro.wasm2j.ops;

import io.github.eutro.wasm2j.ext.ExtHolder;

/**
 * An operation key, representing a type of operation, without intermediates.
 */
public abstract class OpKey extends ExtHolder {
    public String mnemonic;

    public OpKey(String mnemonic) {
        this.mnemonic = mnemonic;
    }

    @Override
    public String toString() {
        return mnemonic;
    }
}

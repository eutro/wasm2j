package io.github.eutro.wasm2j.conf;

import io.github.eutro.wasm2j.conf.api.*;
import io.github.eutro.wasm2j.conf.impl.*;

public class Conventions {
    public static final CallingConvention DEFAULT_CC = new BasicCallingConvention();

    public static WirJavaConventionFactory.Builder createBuilder() {
        return WirJavaConventionFactory.builder();
    }
}

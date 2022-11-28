package io.github.eutro.wasm2j.conf;

import io.github.eutro.wasm2j.conf.api.*;
import io.github.eutro.wasm2j.conf.impl.*;

public class Conventions {
    public static final CallingConvention DEFAULT_CC = new BasicCallingConvention();

    public static final WirJavaConventionFactory DEFAULT_CONVENTIONS =
            createBuilder()
                    .setFunctionImports(Imports.abstractMethodFuncImports(DEFAULT_CC))
                    .build();

    public static DefaultFactory.Builder createBuilder() {
        return DefaultFactory.builder();
    }
}

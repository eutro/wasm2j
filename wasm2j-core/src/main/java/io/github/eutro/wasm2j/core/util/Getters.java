package io.github.eutro.wasm2j.core.util;

import io.github.eutro.wasm2j.core.ops.JavaOps;
import io.github.eutro.wasm2j.core.ssa.IRBuilder;
import io.github.eutro.wasm2j.core.ssa.JClass;
import io.github.eutro.wasm2j.core.ssa.Var;
import org.jetbrains.annotations.Nullable;

/**
 * A collection of utilities for constructing {@link ValueGetter}s and {@link ValueGetterSetter}s.
 */
public class Getters {
    /**
     * A getter that gets {@code this}.
     */
    public static final ValueGetter GET_THIS = IRUtils::getThis;

    /**
     * A getter (and setter) that loads (resp. stores) the field on the owner,
     * which must be null if and only if the field is static.
     *
     * @param owner The owner. Null if and only if the field is static.
     * @param field The field.
     * @return The getter (and setter).
     */
    public static ValueGetterSetter fieldGetter(@Nullable ValueGetter owner, JClass.JavaField field) {
        if (field.isStatic()) {
            if (owner != null) throw new IllegalArgumentException();
        } else {
            if (owner == null) throw new IllegalArgumentException();
        }
        return new ValueGetterSetter() {
            @Override
            public Var get(IRBuilder ib) {
                return ib.insert(JavaOps.GET_FIELD
                                .create(field)
                                .insn(owner == null ? new Var[0] : new Var[]{owner.get(ib)}),
                        "field");
            }

            @Override
            public void set(IRBuilder ib, Var val) {
                ib.insert(JavaOps.PUT_FIELD
                        .create(field)
                        .insn(owner == null ? new Var[]{val} : new Var[]{owner.get(ib), val})
                        .assignTo());
            }
        };
    }

    /**
     * A getter (and setter) that invokes the respective methods on a target.
     *
     * @param target The target to invoke the methods on.
     * @param getter The getter method.
     * @param setter The setter method.
     * @return The getter (and setter).
     */
    public static ValueGetterSetter methodGetterSetter(
            ValueGetter target,
            JClass.JavaMethod getter,
            JClass.JavaMethod setter
    ) {
        return new ValueGetterSetter() {
            @Override
            public Var get(IRBuilder ib) {
                return ib.insert(JavaOps.INVOKE.create(getter).insn(target.get(ib)), "got");
            }

            @Override
            public void set(IRBuilder ib, Var val) {
                ib.insert(JavaOps.INVOKE.create(setter).insn(target.get(ib), val).assignTo());
            }
        };
    }
}

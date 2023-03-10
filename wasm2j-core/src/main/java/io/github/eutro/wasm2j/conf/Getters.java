package io.github.eutro.wasm2j.conf;

import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ssa.IRBuilder;
import io.github.eutro.wasm2j.ssa.JClass;
import io.github.eutro.wasm2j.ssa.Var;
import io.github.eutro.wasm2j.util.IRUtils;
import io.github.eutro.wasm2j.util.ValueGetter;
import io.github.eutro.wasm2j.util.ValueGetterSetter;
import org.jetbrains.annotations.Nullable;

public class Getters {
    public static final ValueGetter GET_THIS = IRUtils::getThis;

    public static ValueGetterSetter fieldGetter(@Nullable ValueGetter owner, JClass.JavaField field) {
        if (field.isStatic) {
            assert owner == null;
        } else {
            assert owner != null;
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

    public static ValueGetterSetter staticGetter(JClass.JavaField field) {
        return fieldGetter(null, field);
    }

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

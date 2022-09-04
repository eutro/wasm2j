package io.github.eutro.wasm2j.intrinsics;

import static io.github.eutro.jwasm.Opcodes.*;

// @formatter:off
public final class Impl {
    @Intrinsic(I32_ADD) static int i32Add(int lhs, int rhs) { return lhs + rhs; }
    @Intrinsic(I32_AND) static int i32And(int lhs, int rhs) { return lhs & rhs; }
    @Intrinsic(I32_MUL) static int i32Mul(int lhs, int rhs) { return lhs * rhs; }
    @Intrinsic(I32_SHL) static int i32Shl(int lhs, int rhs) { return lhs << rhs; }
    @Intrinsic(I32_DIV_S) static int i32DivS(int lhs, int rhs) { return lhs / rhs; }
    @Intrinsic(I32_DIV_U) static int i32DivU(int lhs, int rhs) { return Integer.divideUnsigned(lhs, rhs); }
    @Intrinsic(I32_EQ) static int i32Eq(int a, int b) { return a == b ? 1 : 0; }
    @Intrinsic(I32_NE) static int i32Ne(int a, int b) { return a != b ? 1 : 0; }
    @Intrinsic(I32_GE_S) static int i32GeS(int a, int b) { return a >= b ? 1 : 0; }
    @Intrinsic(I32_EQZ) static int i32Eqz(int x) { return x == 0 ? 1 : 0; }
    @Intrinsic(I64_MUL) static long i64Mul(long lhs, long rhs) { return lhs * rhs; }
}
// @formatter:on

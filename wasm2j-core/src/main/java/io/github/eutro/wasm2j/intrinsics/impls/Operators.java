package io.github.eutro.wasm2j.intrinsics.impls;

import io.github.eutro.wasm2j.intrinsics.Intrinsic;

import static io.github.eutro.jwasm.Opcodes.*;

/**
 * Java implementations of certain WebAssembly primitives.
 */
@SuppressWarnings("DuplicatedCode")
public final class Operators {
    private static final double MAX_ULONG = (double) Long.MAX_VALUE * 2d;
    private static final long MAX_UINT = 0xFFFFFFFFL;

    // @formatter:off

    // region i32
    @Intrinsic(I32_EQZ) public static int i32Eqz(int x) { return x == 0 ? 1 : 0; }
    @Intrinsic(I32_EQ) public static int i32Eq(int x, int y) { return x == y ? 1 : 0; }
    @Intrinsic(I32_NE) public static int i32Ne(int x, int y) { return x != y ? 1 : 0; }
    @Intrinsic(I32_LT_S) public static int i32LtS(int x, int y) { return x < y ? 1 : 0; }
    @Intrinsic(I32_LT_U) public static int i32LtU(int x, int y) { return Integer.compareUnsigned(x, y) < 0 ? 1 : 0; }
    @Intrinsic(I32_GT_S) public static int i32GtS(int x, int y) { return x > y ? 1 : 0; }
    @Intrinsic(I32_GT_U) public static int i32GtU(int x, int y) { return Integer.compareUnsigned(x, y) > 0 ? 1 : 0; }
    @Intrinsic(I32_LE_S) public static int i32LeS(int x, int y) { return x <= y ? 1 : 0; }
    @Intrinsic(I32_LE_U) public static int i32LeU(int x, int y) { return Integer.compareUnsigned(x, y) <= 0 ? 1 : 0; }
    @Intrinsic(I32_GE_S) public static int i32GeS(int x, int y) { return x >= y ? 1 : 0; }
    @Intrinsic(I32_GE_U) public static int i32GeU(int x, int y) { return Integer.compareUnsigned(x, y) >= 0 ? 1 : 0; }
    // endregion
    // region i64
    @Intrinsic(I64_EQZ) public static int i64Eqz(long x) { return x == 0 ? 1 : 0; }
    @Intrinsic(I64_EQ) public static int i64Eq(long x, long y) { return x == y ? 1 : 0; }
    @Intrinsic(I64_NE) public static int i64Ne(long x, long y) { return x != y ? 1 : 0; }
    @Intrinsic(I64_LT_S) public static int i64LtS(long x, long y) { return x < y ? 1 : 0; }
    @Intrinsic(I64_LT_U) public static int i64LtU(long x, long y) { return Long.compareUnsigned(x, y) < 0 ? 1 : 0; }
    @Intrinsic(I64_GT_S) public static int i64GtS(long x, long y) { return x > y ? 1 : 0; }
    @Intrinsic(I64_GT_U) public static int i64GtU(long x, long y) { return Long.compareUnsigned(x, y) > 0 ? 1 : 0; }
    @Intrinsic(I64_LE_S) public static int i64LeS(long x, long y) { return x <= y ? 1 : 0; }
    @Intrinsic(I64_LE_U) public static int i64LeU(long x, long y) { return Long.compareUnsigned(x, y) <= 0 ? 1 : 0; }
    @Intrinsic(I64_GE_S) public static int i64GeS(long x, long y) { return x >= y ? 1 : 0; }
    @Intrinsic(I64_GE_U) public static int i64GeU(long x, long y) { return Long.compareUnsigned(x, y) >= 0 ? 1 : 0; }
    // endregion
    // region f32
    @Intrinsic(F32_EQ) public static int f32Eq(float x, float y) { return x == y ? 1 : 0; }
    @Intrinsic(F32_NE) public static int f32Ne(float x, float y) { return x != y ? 1 : 0; }
    @Intrinsic(F32_LT) public static int f32Lt(float x, float y) { return x < y ? 1 : 0; }
    @Intrinsic(F32_GT) public static int f32Gt(float x, float y) { return x > y ? 1 : 0; }
    @Intrinsic(F32_LE) public static int f32Le(float x, float y) { return x <= y ? 1 : 0; }
    @Intrinsic(F32_GE) public static int f32Ge(float x, float y) { return x >= y ? 1 : 0; }
    // endregion
    // region f64
    @Intrinsic(F64_EQ) public static int f64Eq(double x, double y) { return x == y ? 1 : 0; }
    @Intrinsic(F64_NE) public static int f64Ne(double x, double y) { return x != y ? 1 : 0; }
    @Intrinsic(F64_LT) public static int f64Lt(double x, double y) { return x < y ? 1 : 0; }
    @Intrinsic(F64_GT) public static int f64Gt(double x, double y) { return x > y ? 1 : 0; }
    @Intrinsic(F64_LE) public static int f64Le(double x, double y) { return x <= y ? 1 : 0; }
    @Intrinsic(F64_GE) public static int f64Ge(double x, double y) { return x >= y ? 1 : 0; }
    // endregion
    // endregion
    // region Mathematical
    // region i32
    @Intrinsic(I32_CLZ) public static int i32Clz(int x) { return Integer.numberOfLeadingZeros(x); }
    @Intrinsic(I32_CTZ) public static int i32Ctz(int x) { return Integer.numberOfTrailingZeros(x); }
    @Intrinsic(I32_POPCNT) public static int i32Popcnt(int x) { return Integer.bitCount(x); }
    @Intrinsic(I32_ADD) public static int i32Add(int x, int y) { return x + y; }
    @Intrinsic(I32_SUB) public static int i32Sub(int x, int y) { return x - y; }
    @Intrinsic(I32_MUL) public static int i32Mul(int x, int y) { return x * y; }
    @Intrinsic(value = I32_DIV_S, inline = false) public static int i32DivS(int x, int y) {
        if (x == Integer.MIN_VALUE && y == -1) {
            throw new ArithmeticException("integer overflow");
        }
        return x / y;
    }
    @Intrinsic(I32_DIV_U) public static int i32DivU(int x, int y) { return Integer.divideUnsigned(x, y); }
    @Intrinsic(I32_REM_S) public static int i32RemS(int x, int y) { return x % y; }
    @Intrinsic(I32_REM_U) public static int i32RemU(int x, int y) { return Integer.remainderUnsigned(x, y); }
    @Intrinsic(I32_AND) public static int i32And(int x, int y) { return x & y; }
    @Intrinsic(I32_OR) public static int i32Or(int x, int y) { return x | y; }
    @Intrinsic(I32_XOR) public static int i32Xor(int x, int y) { return x ^ y; }
    @Intrinsic(I32_SHL) public static int i32Shl(int x, int y) { return x << y; }
    @Intrinsic(I32_SHR_S) public static int i32ShrS(int x, int y) { return x >> y; }
    @Intrinsic(I32_SHR_U) public static int i32ShrU(int x, int y) { return x >>> y; }
    @Intrinsic(I32_ROTL) public static int i32Rotl(int x, int y) { return Integer.rotateLeft(x, y); }
    @Intrinsic(I32_ROTR) public static int i32Rotr(int x, int y) { return Integer.rotateRight(x, y); }
    // endregion
    // region i64
    @Intrinsic(I64_CLZ) public static long i64Clz(long x) { return Long.numberOfLeadingZeros(x); }
    @Intrinsic(I64_CTZ) public static long i64Ctz(long x) { return Long.numberOfTrailingZeros(x); }
    @Intrinsic(I64_POPCNT) public static long i64Popcnt(long x) { return Long.bitCount(x); }
    @Intrinsic(I64_ADD) public static long i64Add(long x, long y) { return x + y; }
    @Intrinsic(I64_SUB) public static long i64Sub(long x, long y) { return x - y; }
    @Intrinsic(I64_MUL) public static long i64Mul(long x, long y) { return x * y; }
    @Intrinsic(value = I64_DIV_S, inline = false) public static long i64DivS(long x, long y) {
        if (x == Long.MIN_VALUE && y == -1) {
            throw new ArithmeticException("integer overflow");
        }
        return x / y;
    }
    @Intrinsic(I64_DIV_U) public static long i64DivU(long x, long y) { return Long.divideUnsigned(x, y); }
    @Intrinsic(I64_REM_S) public static long i64RemS(long x, long y) { return x % y; }
    @Intrinsic(I64_REM_U) public static long i64RemU(long x, long y) { return Long.remainderUnsigned(x, y); }
    @Intrinsic(I64_AND) public static long i64And(long x, long y) { return x & y; }
    @Intrinsic(I64_OR) public static long i64Or(long x, long y) { return x | y; }
    @Intrinsic(I64_XOR) public static long i64Xor(long x, long y) { return x ^ y; }
    @Intrinsic(I64_SHL) public static long i64Shl(long x, long y) { return x << y; }
    @Intrinsic(I64_SHR_S) public static long i64ShrS(long x, long y) { return x >> y; }
    @Intrinsic(I64_SHR_U) public static long i64ShrU(long x, long y) { return x >>> y; }
    @Intrinsic(I64_ROTL) public static long i64Rotl(long x, long y) { return Long.rotateLeft(x, (int) y); }
    @Intrinsic(I64_ROTR) public static long i64Rotr(long x, long y) { return Long.rotateRight(x, (int) y); }
    // endregion
    // region f32
    @Intrinsic(F32_ABS) public static float f32Abs(float x) { return Math.abs(x); }
    @Intrinsic(F32_NEG) public static float f32Neg(float x) { return -x; }
    @Intrinsic(F32_CEIL) public static float f32Ceil(float x) { return (float) Math.ceil(x); }
    @Intrinsic(F32_FLOOR) public static float f32Floor(float x) { return (float) Math.floor(x); }
    @Intrinsic(F32_TRUNC) public static float f32Trunc(float x) { return (float) (x < 0 ? Math.ceil(x) : Math.floor(x)); }
    @Intrinsic(F32_NEAREST) public static float f32Nearest(float x) { return (float) Math.rint(x); }
    @Intrinsic(F32_SQRT) public static float f32Sqrt(float x) { return (float) Math.sqrt(x); }
    @Intrinsic(F32_ADD) public static float f32Add(float x, float y) { return x + y; }
    @Intrinsic(F32_SUB) public static float f32Sub(float x, float y) { return x - y; }
    @Intrinsic(F32_MUL) public static float f32Mul(float x, float y) { return x * y; }
    @Intrinsic(F32_DIV) public static float f32Div(float x, float y) { return x / y; }
    @Intrinsic(F32_MIN) public static float f32Min(float x, float y) { return Math.min(x, y); }
    @Intrinsic(F32_MAX) public static float f32Max(float x, float y) { return Math.max(x, y); }
    @Intrinsic(F32_COPYSIGN) public static float f32Copysign(float x, float y) { return Math.copySign(x, y); }
    // endregion
    // region f64
    @Intrinsic(F64_ABS) public static double f64Abs(double x) { return Math.abs(x); }
    @Intrinsic(F64_NEG) public static double f64Neg(double x) { return -x; }
    @Intrinsic(F64_CEIL) public static double f64Ceil(double x) { return Math.ceil(x); }
    @Intrinsic(F64_FLOOR) public static double f64Floor(double x) { return Math.floor(x); }
    @Intrinsic(F64_TRUNC) public static double f64Trunc(double x) { return x < 0 ? Math.ceil(x) : Math.floor(x); }
    @Intrinsic(F64_NEAREST) public static double f64Nearest(double x) { return Math.rint(x); }
    @Intrinsic(F64_SQRT) public static double f64Sqrt(double x) { return Math.sqrt(x); }
    @Intrinsic(F64_ADD) public static double f64Add(double x, double y) { return x + y; }
    @Intrinsic(F64_SUB) public static double f64Sub(double x, double y) { return x - y; }
    @Intrinsic(F64_MUL) public static double f64Mul(double x, double y) { return x * y; }
    @Intrinsic(F64_DIV) public static double f64Div(double x, double y) { return x / y; }
    @Intrinsic(F64_MIN) public static double f64Min(double x, double y) { return Math.min(x, y); }
    @Intrinsic(F64_MAX) public static double f64Max(double x, double y) { return Math.max(x, y); }
    @Intrinsic(F64_COPYSIGN) public static double f64Copysign(double x, double y) { return Math.copySign(x, y); }
    // endregion
    // endregion
    // region Conversions
    @Intrinsic(I32_WRAP_I64) public static int i32WrapI64(long x) { return (int) x; }
    @Intrinsic(value = I32_TRUNC_F32_S, inline = false) public static int i32TruncF32S(float x) {
        if (Float.isNaN(x)) throw new ArithmeticException("invalid conversion to integer");
        if (Float.isInfinite(x)
                // NB: some rounded ints are the first out-of-bounds, some are the last in-bounds
                || x < Integer.MIN_VALUE
                || x >= Integer.MAX_VALUE
        ) {
            throw new ArithmeticException("integer overflow");
        }
        float trunc = (float) (x < 0 ? Math.ceil(x) : Math.floor(x));
        return (int) trunc;
    }
    @Intrinsic(value = I32_TRUNC_F32_U, inline = false) public static int i32TruncF32U(float x) {
        if (Float.isNaN(x)) throw new ArithmeticException("invalid conversion to integer");
        if (Float.isInfinite(x)
                || x <= -1f
                || x >= MAX_UINT
        ) {
            throw new ArithmeticException("integer overflow");
        }
        float trunc = (float) (x < 0 ? Math.ceil(x) : Math.floor(x));
        return (int) (long) trunc;
    }
    @Intrinsic(value = I32_TRUNC_F64_S, inline = false) public static int i32TruncF64S(double x) {
        if (Double.isNaN(x)) throw new ArithmeticException("invalid conversion to integer");
        if (Double.isInfinite(x)
                || x <= Integer.MIN_VALUE - 1d
                || x >= Integer.MAX_VALUE + 1d
        ) {
            throw new ArithmeticException("integer overflow");
        }
        double trunc = x < 0 ? Math.ceil(x) : Math.floor(x);
        return (int) trunc;
    }
    @Intrinsic(value = I32_TRUNC_F64_U, inline = false) public static int i32TruncF64U(double x) {
        if (Double.isNaN(x)) throw new ArithmeticException("invalid conversion to integer");
        if (Double.isInfinite(x)
                || x <= -1
                || x >= MAX_UINT + 1d
        ) {
            throw new ArithmeticException("integer overflow");
        }
        double trunc = x < 0 ? Math.ceil(x) : Math.floor(x);
        return (int) (long) trunc;
    }
    @Intrinsic(I64_EXTEND_I32_S) public static long i64ExtendI32S(int x) { return x; }
    @Intrinsic(I64_EXTEND_I32_U) public static long i64ExtendI32U(int x) { return Integer.toUnsignedLong(x); }
    @Intrinsic(value = I64_TRUNC_F32_S, inline = false) public static long i64TruncF32S(float x) {
        if (Float.isNaN(x)) throw new ArithmeticException("invalid conversion to integer");
        if (Float.isInfinite(x)
                || x < Long.MIN_VALUE
                || x >= Long.MAX_VALUE
        ) {
            throw new ArithmeticException("integer overflow");
        }
        float trunc = (float) (x < 0 ? Math.ceil(x) : Math.floor(x));
        return (long) trunc;
    }
    @Intrinsic(value = I64_TRUNC_F32_U, inline = false) public static long i64TruncF32U(float x) {
        if (Float.isNaN(x)) throw new ArithmeticException("invalid conversion to integer");
        if (Float.isInfinite(x)
                || x <= -1f
                || x >= (float) MAX_ULONG
        ) {
            throw new ArithmeticException("integer overflow");
        }
        float trunc = (float) (x < 0 ? Math.ceil(x) : Math.floor(x));
        if (trunc < 0 || trunc > MAX_ULONG) throw new ArithmeticException();
        if (trunc >= Long.MAX_VALUE - 1) return (long) (trunc / 2F) * 2L;
        return (long) trunc;
    }
    @Intrinsic(value = I64_TRUNC_F64_S, inline = false) public static long i64TruncF64S(double x) {
        if (Double.isNaN(x)) throw new ArithmeticException("invalid conversion to integer");
        if (Double.isInfinite(x)
                || x < Long.MIN_VALUE
                || x >= Long.MAX_VALUE
        ) {
            throw new ArithmeticException("integer overflow");
        }
        double trunc = x < 0 ? Math.ceil(x) : Math.floor(x);
        return (long) trunc;
    }
    @Intrinsic(value = I64_TRUNC_F64_U, inline = false) public static long i64TruncF64U(double x) {
        if (Double.isNaN(x)) throw new ArithmeticException("invalid conversion to integer");
        if (Double.isInfinite(x)
                || x <= -1
                || x >= MAX_ULONG
        ) {
            throw new ArithmeticException("integer overflow");
        }
        double trunc = x < 0 ? Math.ceil(x) : Math.floor(x);
        if (trunc >= Long.MAX_VALUE - 1) return (long) (trunc / 2D) * 2L;
        return (long) trunc;
    }
    @Intrinsic(F32_CONVERT_I32_S) public static float f32ConvertI32S(int x) { return (float) x; }
    @Intrinsic(F32_CONVERT_I32_U) public static float f32ConvertI32U(int x) { return (float) Integer.toUnsignedLong(x); }
    @Intrinsic(F32_CONVERT_I64_S) public static float f32ConvertI64S(long x) { return (float) x; }
    // see Guava https://github.com/google/guava/blob/master/guava/src/com/google/common/primitives/UnsignedLong.java
    @Intrinsic(value = F32_CONVERT_I64_U, inline = false) public static float f32ConvertI64U(long x) { return x >= 0 ? (float) x : (float) ((x >>> 1) | (x & 1)) * 2f; }
    @Intrinsic(F32_DEMOTE_F64) public static float f32DemoteF64(double x) { return (float) x; }
    @Intrinsic(F64_CONVERT_I32_S) public static double f64ConvertI32S(int x) { return x; }
    @Intrinsic(F64_CONVERT_I32_U) public static double f64ConvertI32U(int x) { return Integer.toUnsignedLong(x); }
    @Intrinsic(F64_CONVERT_I64_S) public static double f64ConvertI64S(long x) { return (double) x; }
    @Intrinsic(value = F64_CONVERT_I64_U, inline = false) public static double f64ConvertI64U(long x) { return x >= 0 ? x : ((x >>> 1) | (x & 1)) * 2d; }
    @Intrinsic(F64_PROMOTE_F32) public static double f64PromoteF32(float x) { return x; }
    @Intrinsic(I32_REINTERPRET_F32) public static int i32ReinterpretF32(float x) { return Float.floatToRawIntBits(x); }
    @Intrinsic(I64_REINTERPRET_F64) public static long i64ReinterpretF64(double x) { return Double.doubleToRawLongBits(x); }
    @Intrinsic(F32_REINTERPRET_I32) public static float f32ReinterpretI32(int x) { return Float.intBitsToFloat(x); }
    @Intrinsic(F64_REINTERPRET_I64) public static double f64ReinterpretI64(long x) { return Double.longBitsToDouble(x); }
    // endregion
    // region Extension
    @Intrinsic(I32_EXTEND8_S) public static int i32Extend8S(int x) { return (byte) x; }
    @Intrinsic(I32_EXTEND16_S) public static int i32Extend16S(int x) { return (short) x; }
    @Intrinsic(I64_EXTEND8_S) public static long i64Extend8S(long x) { return (byte) x; }
    @Intrinsic(I64_EXTEND16_S) public static long i64Extend16S(long x) { return (short) x; }
    @Intrinsic(I64_EXTEND32_S) public static long i64Extend32S(long x) { return (int) x; }
    // endregion
    // region Saturating Truncation
    @Intrinsic(iOp = I32_TRUNC_SAT_F32_S) public static int i32TruncSatF32S(float x) { return (int) x; }
    @Intrinsic(iOp = I32_TRUNC_SAT_F32_U, inline = false) public static int i32TruncSatF32U(float x) {
        if (!(x >= 0)) return 0;
        if (x >= MAX_UINT) return -1;
        return (int) (long) x;
    }
    @Intrinsic(iOp = I32_TRUNC_SAT_F64_S) public static int i32TruncSatF64S(double x) { return (int) x; }
    @Intrinsic(iOp = I32_TRUNC_SAT_F64_U, inline = false) public static int i32TruncSatF64U(double x) {
        if (!(x >= 0)) return 0;
        if (x >= MAX_UINT) return -1;
        return (int) (long) x;
    }
    @Intrinsic(iOp = I64_TRUNC_SAT_F32_S) public static long i64TruncSatF32S(float x) { return (long) x; }
    // see Kotlin https://github.com/JetBrains/kotlin/blob/master/libraries/stdlib/unsigned/src/kotlin/UnsignedUtils.kt
    @Intrinsic(iOp = I64_TRUNC_SAT_F32_U, inline = false) public static long i64TruncSatF32U(float x) {
        if (!(x >= 0)) return 0;
        if (x >= MAX_ULONG) return -1;
        if (x >= Long.MAX_VALUE - 1) return (long) (x / 2F) * 2L;
        return (long) x;
    }
    @Intrinsic(iOp = I64_TRUNC_SAT_F64_S) public static long i64TruncSatF64S(double x) { return (long) x; }
    @Intrinsic(iOp = I64_TRUNC_SAT_F64_U, inline = false) public static long i64TruncSatF64U(double x) {
        if (!(x >= 0)) return 0;
        if (x >= MAX_ULONG) return -1;
        if (x >= Long.MAX_VALUE - 1) return (long) (x / 2D) * 2L;
        return (long) x;
    }
    // endregion
}
// @formatter:on

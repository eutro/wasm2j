package io.github.eutro.wasm2j.util;

import io.github.eutro.wasm2j.conf.impl.BasicCallingConvention;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ops.*;
import io.github.eutro.wasm2j.ssa.*;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import static io.github.eutro.jwasm.Opcodes.I32;
import static io.github.eutro.wasm2j.ops.JavaOps.JumpType.IFEQ;
import static io.github.eutro.wasm2j.ops.JavaOps.JumpType.IF_ICMPLE;

public class IRUtils {
    public static final JavaExts.JavaClass BYTE_BUFFER_CLASS = new JavaExts.JavaClass(Type.getInternalName(ByteBuffer.class));
    public static final JavaExts.JavaClass METHOD_HANDLE_CLASS = new JavaExts.JavaClass(Type.getInternalName(MethodHandle.class));
    public static final JavaExts.JavaClass MTY_CLASS = new JavaExts.JavaClass(Type.getInternalName(MethodType.class));
    public static final JavaExts.JavaMethod MTY_METHOD_TYPE = new JavaExts.JavaMethod(
            MTY_CLASS,
            "methodType",
            Type.getMethodType(
                    Type.getType(MethodType.class),
                    Type.getType(Class.class),
                    Type.getType(Class[].class)
            ).getDescriptor(),
            JavaExts.JavaMethod.Kind.STATIC
    );

    public static Var getThis(IRBuilder ib) {
        return ib.insert(JavaOps.THIS.insn(), "this");
    }

    public static Insn loadClass(Type ty) {
        switch (ty.getSort()) {
            case Type.OBJECT:
            case Type.ARRAY:
                return CommonOps.constant(ty);
            default: {
                Class<?> boxedTy;
                switch (ty.getSort()) {
                    // @formatter:off
                    case Type.VOID: boxedTy = Void.class; break;
                    case Type.BOOLEAN: boxedTy = Boolean.class; break;
                    case Type.CHAR: boxedTy = Character.class; break;
                    case Type.BYTE: boxedTy = Byte.class; break;
                    case Type.SHORT: boxedTy = Short.class; break;
                    case Type.INT: boxedTy = Integer.class; break;
                    case Type.FLOAT: boxedTy = Float.class; break;
                    case Type.LONG: boxedTy = Long.class; break;
                    case Type.DOUBLE: boxedTy = Double.class; break;
                    // @formatter:on
                    default:
                        throw new IllegalArgumentException();
                }
                return JavaOps.GET_FIELD.create(new JavaExts.JavaField(
                        new JavaExts.JavaClass(Type.getInternalName(boxedTy)),
                        "TYPE",
                        Type.getDescriptor(Class.class),
                        true
                )).insn();
            }
        }
    }

    public static Var getAddr(IRBuilder ib, WasmOps.WithMemArg<?> wmArg, Var ptr) {
        return wmArg.offset == 0
                ? ptr
                : ib.insert(JavaOps.L2I_EXACT.insn(
                ib.insert(JavaOps.LADD.insn(
                        ib.insert(JavaOps.I2L_U.insn(ptr), "ptrL"),
                        ib.insert(CommonOps.constant(Integer.toUnsignedLong(wmArg.offset)), "offset")
                ), "addrL")
        ), "addr");
    }

    public static void lenLoop(IRBuilder ib, Var[] toInc, boolean invert, Var len, Consumer<Var[]> f) {
        BasicBlock srcBlock = ib.getBlock();
        BasicBlock condBlock = ib.func.newBb();
        BasicBlock mvBlock = ib.func.newBb();
        BasicBlock endBlock = ib.func.newBb();

        List<BasicBlock> preds = Arrays.asList(srcBlock, mvBlock);
        Var[] allLoopVars = Arrays.copyOf(toInc, toInc.length + 1);
        allLoopVars[toInc.length] = len;
        Var[] loopSuccs = new Var[allLoopVars.length];
        Var[] loopVs = new Var[allLoopVars.length];

        if (invert) {
            for (int i = 0; i < toInc.length; i++) {
                allLoopVars[i] = ib.insert(JavaOps.ISUB.insn(ib.insert(JavaOps.IADD.insn(allLoopVars[i], len), "n-i"),
                        ib.insert(CommonOps.constant(1), "1")), "n-i-1");
            }
        }
        ib.insertCtrl(Control.br(condBlock));
        ib.setBlock(condBlock);
        for (int i = 0; i < allLoopVars.length; i++) {
            Var succ = ib.func.newVar("i");
            loopSuccs[i] = succ;
            loopVs[i] = ib.insert(CommonOps.PHI.create(preds)
                    .insn(allLoopVars[i], succ), "i");
        }
        Var lenV = loopVs[toInc.length];
        ib.insertCtrl(JavaOps.BR_COND.create(IFEQ).insn(lenV).jumpsTo(endBlock, mvBlock));
        ib.setBlock(mvBlock);

        f.accept(loopVs);

        for (int i = 0; i < allLoopVars.length; i++) {
            Op op = invert || i == toInc.length ? JavaOps.ISUB : JavaOps.IADD;
            ib.insert(op.insn(loopVs[i], ib.insert(CommonOps.constant(1), "i")), loopSuccs[i]);
        }

        ib.insertCtrl(Control.br(condBlock));
        preds.set(1, ib.getBlock());

        ib.setBlock(endBlock);
    }

    public static Var emitNullableInt(IRBuilder ib, @Nullable Integer num) {
        return num == null
                ? ib.insert(CommonOps.constant(null), "nil")
                : BasicCallingConvention.maybeBoxed(ib, ib.insert(CommonOps.constant(num), "n"),
                I32, Type.getType(Integer.class));
    }

    //@formatter:off
    public interface StoreOrLoadFn { void call(int index, Var idx, Var val); }
    public interface BoundsCheckFn<T> { void call(T t, IRBuilder ib, int index, Var value); }
    //@formatter:on
    public static <T> void emitFill(IRBuilder ib,
                                    Effect effect,
                                    UnaryOpKey<Integer> key,
                                    StoreOrLoadFn store,
                                    T t,
                                    BoundsCheckFn<T> emitBoundsCheck) {
        int thisIdx = key.cast(effect.insn().op).arg;
        Iterator<Var> iter = effect.insn().args.iterator();
        Var idx = iter.next();
        Var value = iter.next();
        Var len = iter.next();

        emitBoundsCheck.call(t, ib, thisIdx,
                ib.insert(JavaOps.LADD.insn(
                                ib.insert(JavaOps.I2L_U.insn(idx), "iL"),
                                ib.insert(JavaOps.I2L_U.insn(len), "lenL")
                        ),
                        "idxEnd"));
        IRUtils.lenLoop(ib, new Var[]{idx}, false, len, vars -> store.call(thisIdx, vars[0], value));
    }

    public static <T> void emitCopy(IRBuilder ib,
                                    Effect effect,
                                    UnaryOpKey<Pair<Integer, Integer>> key,
                                    StoreOrLoadFn load,
                                    StoreOrLoadFn store,
                                    T srcT, T dstT,
                                    BoundsCheckFn<T> emitBoundsCheck) {
        Pair<Integer, Integer> arg = key.cast(effect.insn().op).arg;
        int thisIdx = arg.left;
        int otherIdx = arg.right;
        Iterator<Var> iter = effect.insn().args.iterator();
        Var dstAddr = iter.next();
        Var srcAddr = iter.next();
        Var len = iter.next();

        Var lenLong = ib.insert(JavaOps.I2L_U.insn(len), "lenL");
        emitBoundsCheck.call(srcT, ib, thisIdx,
                ib.insert(JavaOps.LADD.insn(ib.insert(JavaOps.I2L_U.insn(srcAddr), "sL"), lenLong), "srcEnd"));
        emitBoundsCheck.call(dstT, ib, otherIdx,
                ib.insert(JavaOps.LADD.insn(ib.insert(JavaOps.I2L_U.insn(dstAddr), "dL"), lenLong), "dstEnd"));

        BasicBlock endBb = ib.func.newBb();
        BasicBlock leBlock = ib.func.newBb(); // d <= s
        BasicBlock gtBlock = ib.func.newBb(); // else

        ib.insertCtrl(JavaOps.BR_COND.create(IF_ICMPLE).insn(dstAddr, srcAddr).jumpsTo(leBlock, gtBlock));
        for (BasicBlock block : new BasicBlock[]{leBlock, gtBlock}) {
            ib.setBlock(block);
            IRUtils.lenLoop(ib, new Var[]{dstAddr, srcAddr}, block == gtBlock, len, vars -> {
                Var x = ib.func.newVar("x");
                load.call(thisIdx, vars[1], x);
                store.call(otherIdx, vars[0], x);
            });
            ib.insertCtrl(Control.br(endBb));
        }
        ib.setBlock(endBb);
    }

    public static void trapWhen(IRBuilder ib, Insn insn, String msg) {
        BasicBlock errBb = ib.func.newBb();
        BasicBlock contBb = ib.func.newBb();
        ib.insertCtrl(insn.jumpsTo(errBb, contBb));
        ib.setBlock(errBb);
        ib.insertCtrl(CommonOps.TRAP.create(msg).insn().jumpsTo());
        ib.setBlock(contBb);
    }
}

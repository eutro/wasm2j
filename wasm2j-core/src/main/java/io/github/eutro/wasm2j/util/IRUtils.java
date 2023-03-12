package io.github.eutro.wasm2j.util;

import io.github.eutro.wasm2j.conf.impl.BasicCallingConvention;
import io.github.eutro.wasm2j.ops.*;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.ssa.JClass.JavaMethod;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static io.github.eutro.jwasm.Opcodes.I32;
import static io.github.eutro.wasm2j.ops.JavaOps.JumpType.IFEQ;
import static io.github.eutro.wasm2j.ops.JavaOps.JumpType.IF_ICMPLE;

/**
 * A set of utilities for working with Wasm2j's IR.
 */
public class IRUtils {
    /**
     * An empty {@link JClass} of {@link ByteBuffer}.
     */
    public static final JClass BYTE_BUFFER_CLASS = JClass.emptyFromJava(ByteBuffer.class);
    /**
     * An empty {@link JClass} of {@link Buffer}.
     */
    public static final JClass BUFFER_CLASS = JClass.emptyFromJava(Buffer.class);
    /**
     * An empty {@link JClass} of {@link MethodHandle}.
     */
    public static final JClass METHOD_HANDLE_CLASS = JClass.emptyFromJava(MethodHandle.class);
    /**
     * An empty {@link JClass} of {@link MethodType}.
     */
    public static final JClass MTY_CLASS = JClass.emptyFromJava(MethodType.class);

    /**
     * An empty {@link JClass} of {@link Base64}.
     */
    public static final JClass BASE64_CLASS = JClass.emptyFromJava(Base64.class);
    /**
     * An empty {@link JClass} of {@link Base64.Decoder}.
     */
    public static final JClass BASE64_DECODER_CLASS = JClass.emptyFromJava(Base64.Decoder.class);

    /**
     * A {@link JavaMethod} of {@link MethodType#methodType(Class, Class[])}.
     */
    public static final JavaMethod MTY_METHOD_TYPE = MTY_CLASS.lookupMethod(
            "methodType",
            Class.class,
            Class[].class);

    /**
     * The maximum value of an unsigned 16-byte integer.
     */
    public static final int MAX_USHORT = (1 << 16) - 1;

    /**
     * Insert an instruction to get the implicit Java receiver in an instance method, {@code this}.
     *
     * @param ib The instruction builder.
     * @return The loaded {@code this}.
     */
    public static Var getThis(IRBuilder ib) {
        return ib.insert(JavaOps.THIS.insn(), "this");
    }

    /**
     * {@code void.class}
     */
    private static final Op VOID_TYPE = JavaOps.GET_FIELD.create(JClass.JavaField.fromJava(Void.class, "TYPE"));
    /**
     * {@code boolean.class}
     */
    private static final Op BOOLEAN_TYPE = JavaOps.GET_FIELD.create(JClass.JavaField.fromJava(Boolean.class, "TYPE"));
    /**
     * {@code char.class}
     */
    private static final Op CHAR_TYPE = JavaOps.GET_FIELD.create(JClass.JavaField.fromJava(Character.class, "TYPE"));
    /**
     * {@code byte.class}
     */
    private static final Op BYTE_TYPE = JavaOps.GET_FIELD.create(JClass.JavaField.fromJava(Byte.class, "TYPE"));
    /**
     * {@code short.class}
     */
    private static final Op SHORT_TYPE = JavaOps.GET_FIELD.create(JClass.JavaField.fromJava(Short.class, "TYPE"));
    /**
     * {@code int.class}
     */
    private static final Op INT_TYPE = JavaOps.GET_FIELD.create(JClass.JavaField.fromJava(Integer.class, "TYPE"));
    /**
     * {@code float.class}
     */
    private static final Op FLOAT_TYPE = JavaOps.GET_FIELD.create(JClass.JavaField.fromJava(Float.class, "TYPE"));
    /**
     * {@code long.class}
     */
    private static final Op LONG_TYPE = JavaOps.GET_FIELD.create(JClass.JavaField.fromJava(Long.class, "TYPE"));
    /**
     * {@code double.class}
     */
    private static final Op DOUBLE_TYPE = JavaOps.GET_FIELD.create(JClass.JavaField.fromJava(Double.class, "TYPE"));

    /**
     * Get the instruction for loading the class described by the given type.
     *
     * @param ty The type.
     * @return The instruction to load the class for {@code ty}.
     */
    public static Insn loadClass(Type ty) {
        switch (ty.getSort()) {
            case Type.OBJECT:
            case Type.ARRAY:
                return CommonOps.constant(ty);
            default: {
                switch (ty.getSort()) {
                    // @formatter:off
                    case Type.VOID: return VOID_TYPE.insn();
                    case Type.BOOLEAN: return BOOLEAN_TYPE.insn();
                    case Type.CHAR: return CHAR_TYPE.insn();
                    case Type.BYTE: return BYTE_TYPE.insn();
                    case Type.SHORT: return SHORT_TYPE.insn();
                    case Type.INT: return INT_TYPE.insn();
                    case Type.FLOAT: return FLOAT_TYPE.insn();
                    case Type.LONG: return LONG_TYPE.insn();
                    case Type.DOUBLE: return DOUBLE_TYPE.insn();
                    // @formatter:on
                    default:
                        throw new IllegalArgumentException();
                }
            }
        }
    }

    /**
     * Get the instruction which computes the offset address of an instruction.
     *
     * @param ib    The instruction builder.
     * @param wmArg The argument containing the offset.
     * @param ptr   The un-offset pointer.
     * @return The offset pointer.
     */
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

    /**
     * Create a loop. This API should not be considered stable.
     *
     * @param ib     The instruction builder.
     * @param toInc  The list of loop variables.
     * @param invert True if loop variables should be decremented, false if they should be incremented.
     * @param len    The number of iterations.
     * @param f      The function creating the loop body, given an array of the loop variables.
     *               This will include all of {@code toInc} (incremented as appropriate),
     *               and a single variable which will go from 0 to {@code len}.
     */
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

    /**
     * Emit a boxed integer, possibly null.
     *
     * @param ib  The instruction builder.
     * @param num The integer.
     * @return The loaded boxed integer.
     */
    public static Var emitNullableInt(IRBuilder ib, @Nullable Integer num) {
        return num == null
                ? ib.insert(CommonOps.constant(null), "nil")
                : BasicCallingConvention.maybeBoxed(ib, ib.insert(CommonOps.constant(num), "n"),
                I32, Type.getType(Integer.class));
    }

    /**
     * The threshold above which gzipped-base64 will be used to store datas inline instead.
     */
    private static final int FILL_DIRECT_MAX = 256;

    /**
     * Emit code to fill a byte buffer with static data.
     *
     * @param data  The data.
     * @param ib    The instruction builder.
     * @param dataV The byte buffer.
     */
    public static void fillAuto(byte[] data, IRBuilder ib, Var dataV) {
        if (data.length <= FILL_DIRECT_MAX) {
            fillWithDirectPuts(data, ib, dataV);
        } else {
            fillWithBase64(data, ib, dataV);
        }
    }

    /**
     * Emit code to fill a byte buffer with static data using direct
     * {@link ByteBuffer#put(byte)} and {@link ByteBuffer#putShort(short)} calls.
     * <p>
     * This may emit a method too large if the static data is too long.
     *
     * @param data  The data.
     * @param ib    The instruction builder.
     * @param dataV The byte buffer.
     */
    public static void fillWithDirectPuts(byte[] data, IRBuilder ib, Var dataV) {
        // NB: Wasm memory is little endian, but when we write
        // data segments we're calling #slice() first, which
        // is always big endian
        ByteBuffer buf = ByteBuffer.wrap(data);

        JavaMethod putShort = BYTE_BUFFER_CLASS.lookupMethod("putShort", short.class);
        JavaMethod putByte = BYTE_BUFFER_CLASS.lookupMethod("put", byte.class);

        while (buf.remaining() >= Short.BYTES) {
            dataV = ib.insert(JavaOps.INVOKE.create(putShort)
                            .insn(dataV, ib.insert(CommonOps.constant((int) buf.getShort()), "s")),
                    "put");
        }

        while (buf.hasRemaining()) {
            dataV = ib.insert(JavaOps.INVOKE.create(putByte)
                            .insn(dataV, ib.insert(CommonOps.constant((int) buf.get()), "b")),
                    "put");
        }
    }

    /**
     * Emit code to fill a byte buffer with static data using gzipped-base64 strings.
     * <p>
     * This will be able to embed more data than {@link #fillWithDirectPuts(byte[], IRBuilder, Var)},
     * and will split the string to create constants short enough for the constant pool. It still
     * cannot embed unbounded data, but we can't do much better.
     *
     * @param data  The data.
     * @param ib    The instruction builder.
     * @param dataV The byte buffer.
     */
    @SuppressWarnings("CommentedOutCode")
    public static void fillWithBase64(byte[] data, IRBuilder ib, Var dataV) {
        byte[] encodedData = data;
        {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gos = new GZIPOutputStream(baos)) {
                gos.write(encodedData);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            encodedData = baos.toByteArray();
        }
        String srcString = Base64.getEncoder().encodeToString(encodedData);

        Var len = ib.insert(CommonOps.constant(data.length), "len");

        Var decoded;
        JavaMethod getDecoder = BASE64_CLASS.lookupMethod("getDecoder");
        JavaMethod decode = BASE64_DECODER_CLASS.lookupMethod("decode", String.class);
        Var decoder = ib.insert(JavaOps.INVOKE.create(getDecoder).insn(), "decoder");
        if (srcString.length() > MAX_USHORT) {
            int strLen = srcString.length();
            // length must be divisible by 4
            int maxLen = MAX_USHORT - (MAX_USHORT % 4);
            String[] subStrings = new String[(strLen - 1) / maxLen + 1];
            for (int i = 0; i < subStrings.length; i++) {
                subStrings[i] = srcString.substring(i * maxLen,
                        Math.min(strLen, (i + 1) * maxLen));
            }

            /*
            Base64.Decoder dec = Base64.getDecoder();
            ByteArrayOutputStream baos = new ByteArrayOutputStream(len);
            for (String str : subStrings) {
                baos.write(dec.decode(str));
            }
            byte[] data = baos.toByteArray();
             */
            String baosName = "java/io/ByteArrayOutputStream";
            Var baos = ib.insert(JavaOps.insns(
                            new TypeInsnNode(Opcodes.NEW, baosName),
                            new InsnNode(Opcodes.DUP_X1),
                            new InsnNode(Opcodes.SWAP),
                            new MethodInsnNode(Opcodes.INVOKESPECIAL, baosName, "<init>", "(I)V"))
                    .insn(len), "baos");
            JavaMethod osWrite = JClass.emptyFromJava(OutputStream.class).lookupMethod("write", byte[].class);
            for (String str : subStrings) {
                Var constStr = ib.insert(CommonOps.constant(str), "str");
                Var part = ib.insert(JavaOps.INVOKE.create(decode).insn(decoder, constStr), "decodedPart");
                ib.insert(JavaOps.INVOKE.create(osWrite).insn(baos, part).assignTo());
            }
            JavaMethod baosToArray = JClass.emptyFromJava(ByteArrayOutputStream.class).lookupMethod("toByteArray");
            decoded = ib.insert(JavaOps.INVOKE.create(baosToArray).insn(baos), "decoded");
        } else {
            // byte[] data = Base64.getDecoder().decode(srcString);
            Var constString = ib.insert(CommonOps.constant(srcString), "dataStr");
            decoded = ib.insert(JavaOps.INVOKE.create(decode).insn(decoder, constString), "decoded");
        }

        JavaMethod putBytes = BYTE_BUFFER_CLASS.lookupMethod(
                "put",
                byte[].class, int.class, int.class
        );

        {
            String gzipInputStream = Type.getInternalName(GZIPInputStream.class);
            String byteArrayInputStream = Type.getInternalName(ByteArrayInputStream.class);
            /*
            GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(decoded));
            byte[] decompressed = new byte[data.init.length];
            int o = 0;
            do {
                int n = gis.read(decompressed, o, data.init.length - o);
                o += n;
            } while (o < data.init.length);
             */
            Var gis = ib.insert(JavaOps.insns(// [B
                                    new TypeInsnNode(Opcodes.NEW, byteArrayInputStream), // [B LBaos;
                                    new InsnNode(Opcodes.DUP_X1), // LBaos; [B LBaos;
                                    new InsnNode(Opcodes.SWAP), // LBaos; LBaos; [B
                                    new MethodInsnNode(Opcodes.INVOKESPECIAL, byteArrayInputStream,
                                            "<init>", "([B)V", false), // LBaos;
                                    new TypeInsnNode(Opcodes.NEW, gzipInputStream), // LBaos; LGis;
                                    new InsnNode(Opcodes.DUP_X1), // LGis; LBaos; LGis;
                                    new InsnNode(Opcodes.SWAP), // LGis; LGis; LBaos;
                                    new MethodInsnNode(Opcodes.INVOKESPECIAL, gzipInputStream,
                                            "<init>", "(Ljava/io/InputStream;)V", false)) // LGis;
                            .insn(decoded),
                    "gis");
            decoded = ib.insert(JavaOps.insns(new IntInsnNode(Opcodes.NEWARRAY, Opcodes.T_BYTE)).insn(len),
                    "decompressed");
            BasicBlock loopBlock = ib.func.newBb();
            BasicBlock contBlock = ib.func.newBb();
            Var zero = ib.insert(CommonOps.constant(0), "0");
            ib.insertCtrl(Control.br(loopBlock));
            BasicBlock srcBlock = ib.getBlock();
            ib.setBlock(loopBlock);
            Var offsetOut = ib.func.newVar("o'");
            Var offset = ib.insert(CommonOps.PHI
                            .create(Arrays.asList(srcBlock, loopBlock))
                            .insn(zero, offsetOut),
                    "offset");
            // offset.attachExt(JavaExts.TYPE, Type.INT_TYPE);
            Var limit = ib.insert(JavaOps.ISUB.insn(len, offset), "limit");
            Var read = ib.insert(JavaOps.INVOKE.create(JClass.emptyFromJava(InputStream.class)
                                    .lookupMethod("read", byte[].class, int.class, int.class))
                            .insn(gis, decoded, offset, limit),
                    "read");
            ib.insert(JavaOps.IADD.insn(offset, read).assignTo(offsetOut));
            ib.insertCtrl(JavaOps.BR_COND.create(JavaOps.JumpType.IF_ICMPLT)
                    .insn(offset, len)
                    .jumpsTo(loopBlock, contBlock));
            ib.setBlock(contBlock);
        }

        ib.insert(JavaOps.INVOKE.create(putBytes).insn(dataV, decoded,
                ib.insert(CommonOps.constant(0), "0"), len), "put");
    }

    //@formatter:off
    /** Not stable. */
    public interface StoreOrLoadFn { void call(int index, Var idx, Var val); }
    /** Not stable. */
    public interface BoundsCheckFn<T> { void call(T t, IRBuilder ib, int index, Var value); }
    //@formatter:on

    /**
     * Not stable.
     */
    public static <T> void emitFill(IRBuilder ib,
                                    Effect effect,
                                    UnaryOpKey<Integer> key,
                                    StoreOrLoadFn store,
                                    T t,
                                    BoundsCheckFn<T> emitBoundsCheck) {
        int thisIdx = key.cast(effect.insn().op).arg;
        Iterator<Var> iter = effect.insn().args().iterator();
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

    /**
     * Not stable.
     */
    @SuppressWarnings("DuplicatedCode")
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
        Iterator<Var> iter = effect.insn().args().iterator();
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

    /**
     * Emit code to trap with the given message if a given instruction jumps.
     *
     * @param ib The instruction builder.
     * @param insn The jump instruction.
     * @param msg The message.
     */
    public static void trapWhen(IRBuilder ib, Insn insn, String msg) {
        BasicBlock errBb = ib.func.newBb();
        BasicBlock contBb = ib.func.newBb();
        ib.insertCtrl(insn.jumpsTo(errBb, contBb));
        ib.setBlock(errBb);
        ib.insertCtrl(CommonOps.TRAP.create(msg).insn().jumpsTo());
        ib.setBlock(contBb);
    }
}

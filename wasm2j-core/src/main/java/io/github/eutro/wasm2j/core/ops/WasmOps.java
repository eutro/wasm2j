package io.github.eutro.wasm2j.core.ops;

import io.github.eutro.jwasm.tree.TypeNode;
import io.github.eutro.wasm2j.core.ssa.BasicBlock;
import io.github.eutro.wasm2j.core.ssa.Control;
import io.github.eutro.wasm2j.core.ssa.Var;
import io.github.eutro.wasm2j.core.util.Disassembler;
import io.github.eutro.wasm2j.core.ext.CommonExts;
import io.github.eutro.wasm2j.core.util.Pair;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.ByteBuffer;

import static io.github.eutro.jwasm.Opcodes.*;

public class WasmOps {
    // break convention is that the last target is the fallback, while the first targets are taken conditionally
    public static final Op BR_IF = new SimpleOpKey("br_if").create();
    public static final Op BR_TABLE = new SimpleOpKey("br_table").create();

    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE_USE)
    public @interface For {
        String value();
    }

    public static final UnaryOpKey<@For("var") Integer> GLOBAL_REF = new UnaryOpKey<>("global.ref");
    public static final UnaryOpKey<@For("var") Integer> GLOBAL_SET = new UnaryOpKey<>("global.set");

    public static final UnaryOpKey<WithMemArg<StoreType>> MEM_STORE = new UnaryOpKey<>("mem.set");
    public static final UnaryOpKey<WithMemArg<DerefType>> MEM_LOAD = new UnaryOpKey<>("mem.load");
    public static final UnaryOpKey<@For("memory") Integer> MEM_SIZE = new UnaryOpKey<>("mem.length");
    public static final UnaryOpKey<@For("memory") Integer> MEM_GROW = new UnaryOpKey<>("mem.grow");
    public static final UnaryOpKey<Pair<@For("memory") Integer, @For("data") Integer>> MEM_INIT = new UnaryOpKey<>("mem.init");
    public static final UnaryOpKey<@For("data") Integer> DATA_DROP = new UnaryOpKey<>("data.drop");
    public static final UnaryOpKey<Pair<@For("src") Integer, @For("dst") Integer>> MEM_COPY = new UnaryOpKey<>("mem.copy");
    public static final UnaryOpKey<@For("memory") Integer> MEM_FILL = new UnaryOpKey<>("mem.fill");

    public static final UnaryOpKey<@For("table") Integer> TABLE_STORE = new UnaryOpKey<>("table.set");
    public static final UnaryOpKey<@For("table") Integer> TABLE_REF = new UnaryOpKey<>("table.ref");
    public static final UnaryOpKey<@For("table") Integer> TABLE_SIZE = new UnaryOpKey<>("table.size");
    public static final UnaryOpKey<@For("table") Integer> TABLE_GROW = new UnaryOpKey<>("table.grow");
    public static final UnaryOpKey<Pair<@For("table") Integer, @For("elem") Integer>> TABLE_INIT = new UnaryOpKey<>("table.init");
    public static final UnaryOpKey<@For("elem") Integer> ELEM_DROP = new UnaryOpKey<>("elem.drop");
    public static final UnaryOpKey<Pair<@For("src") Integer, @For("dst") Integer>> TABLE_COPY = new UnaryOpKey<>("table.copy");
    public static final UnaryOpKey<@For("table") Integer> TABLE_FILL = new UnaryOpKey<>("table.fill");

    public static final UnaryOpKey<@For("func") Integer> FUNC_REF = new UnaryOpKey<>("func.ref");
    public static final UnaryOpKey<CallType> CALL = new UnaryOpKey<>("call");
    public static final UnaryOpKey<TypeNode> CALL_INDIRECT = new UnaryOpKey<>("call_indirect");

    public static final UnaryOpKey<@For("type") Byte> ZEROINIT = new UnaryOpKey<>("zeroinit");

    public static final SimpleOpKey IS_NULL = new SimpleOpKey("is_null");

    public static final SimpleOpKey SELECT = new SimpleOpKey("select"); /* cond ift iff */

    public static final UnaryOpKey<OperatorType> OPERATOR = new UnaryOpKey<>("op");

    public static Control brIf(Var cond, BasicBlock thenB, BasicBlock elseB) {
        return BR_IF.insn(cond).jumpsTo(thenB, elseB);
    }

    static {
        for (OpKey key : new OpKey[]{
                GLOBAL_REF,
                ZEROINIT,
                FUNC_REF,
                MEM_SIZE,
                IS_NULL,
                SELECT
        }) {
            key.attachExt(CommonExts.IS_PURE, true);
        }
    }

    public static class CallType {
        public int func;
        public TypeNode type;

        public CallType(int func, TypeNode type) {
            this.func = func;
            this.type = type;
        }

        @Override
        public String toString() {
            return "func=" + func;
        }
    }

    public static class WithMemArg<T> {
        public T value;
        public int memory = 0;
        public int offset;

        private WithMemArg(T value, int offset) {
            this.value = value;
            this.offset = offset;
        }

        public static <T> WithMemArg<T> create(T value, int offset) {
            return new WithMemArg<>(value, offset);
        }

        @Override
        public String toString() {
            return "offset=" + offset + " " + value;
        }
    }

    public static class DerefType {
        public byte outType;
        public LoadType load;
        public ExtType ext;
        private final byte opcode;

        public DerefType(byte opcode, byte outType, LoadType load, ExtType ext) {
            this.opcode = opcode;
            this.outType = outType;
            this.load = load;
            this.ext = ext;
        }

        @Override
        public String toString() {
            return String.format("(%s) load %s", ext, load);
        }

        public static DerefType fromOpcode(byte opcode) {
            switch (opcode) {
                // @formatter:off
                case I32_LOAD: return new DerefType(I32_LOAD, I32, LoadType.I32, ExtType.NOEXT);
                case I64_LOAD: return new DerefType(I64_LOAD, I64, LoadType.I64, ExtType.NOEXT);
                case F32_LOAD: return new DerefType(F32_LOAD, F32, LoadType.F32, ExtType.NOEXT);
                case F64_LOAD: return new DerefType(F64_LOAD, F64, LoadType.F64, ExtType.NOEXT);
                case I32_LOAD8_S: return new DerefType(I32_LOAD8_S, I32, LoadType.I8, ExtType.S8_32);
                case I32_LOAD8_U: return new DerefType(I32_LOAD8_U, I32, LoadType.I8, ExtType.U8_32);
                case I32_LOAD16_S: return new DerefType(I32_LOAD16_S, I32, LoadType.I16, ExtType.S16_32);
                case I32_LOAD16_U: return new DerefType(I32_LOAD16_U, I32, LoadType.I16, ExtType.U16_32);
                case I64_LOAD8_S: return new DerefType(I64_LOAD8_S, I64, LoadType.I8, ExtType.S8_64);
                case I64_LOAD8_U: return new DerefType(I64_LOAD8_U, I64, LoadType.I8, ExtType.U8_64);
                case I64_LOAD16_S: return new DerefType(I64_LOAD16_S, I64, LoadType.I16, ExtType.S16_64);
                case I64_LOAD16_U: return new DerefType(I64_LOAD16_U, I64, LoadType.I16, ExtType.U16_64);
                case I64_LOAD32_S: return new DerefType(I64_LOAD32_S, I64, LoadType.I32, ExtType.S32_64);
                case I64_LOAD32_U: return new DerefType(I64_LOAD32_U, I64, LoadType.I32, ExtType.U32_64);
                // @formatter:on
                default:
                    throw new IllegalArgumentException();
            }
        }

        public byte getOpcode() {
            return opcode;
        }

        public enum LoadType {
            I8("get", "(I)B"),
            I16("getShort", "(I)S"),
            I32("getInt", "(I)I"),
            I64("getLong", "(I)J"),
            F32("getFloat", "(I)F"),
            F64("getDouble", "(I)D"),
            ;

            public final String funcName;
            public final String desc;

            LoadType(String funcName, String desc) {
                this.funcName = funcName;
                this.desc = desc;
            }
        }

        public enum ExtType {
            NOEXT,

            S8_32(/* noop */),
            S16_32(/* noop */),
            S8_64(Opcodes.I2L),
            S16_64(Opcodes.I2L),
            S32_64(Opcodes.I2L),

            U8_32(Byte.class, "toUnsignedInt", "(B)I"),
            U16_32(Short.class, "toUnsignedInt", "(S)I"),
            U8_64(Byte.class, "toUnsignedLong", "(B)J"),
            U16_64(Short.class, "toUnsignedLong", "(S)J"),
            U32_64(Integer.class, "toUnsignedLong", "(I)J"),
            ;

            public final InsnList insns;

            ExtType(int opcode) {
                this(new InsnNode(opcode));
            }

            ExtType(Class<?> cls, String name, String desc) {
                this(new MethodInsnNode(
                        Opcodes.INVOKESTATIC,
                        Type.getInternalName(cls),
                        name,
                        desc,
                        false
                ));
            }

            ExtType(AbstractInsnNode... insns) {
                this.insns = new InsnList();
                for (AbstractInsnNode insn : insns) {
                    this.insns.add(insn);
                }
            }
        }
    }

    public enum StoreType {
        F32("putFloat", "(IF)Ljava/nio/ByteBuffer;"),
        F64("putDouble", "(ID)Ljava/nio/ByteBuffer;"),

        I32_8("put", "(IB)Ljava/nio/ByteBuffer;"),
        I32_16("putShort", "(IS)Ljava/nio/ByteBuffer;"),
        I32("putInt", "(II)Ljava/nio/ByteBuffer;"),

        I64_8(Opcodes.L2I, "put", "(IB)Ljava/nio/ByteBuffer;"),
        I64_16(Opcodes.L2I, "putShort", "(IS)Ljava/nio/ByteBuffer;"),
        I64_32(Opcodes.L2I, "putInt", "(II)Ljava/nio/ByteBuffer;"),
        I64("putLong", "(IJ)Ljava/nio/ByteBuffer;"),
        ;

        public final InsnList insns = new InsnList();

        public byte getType() {
            switch (this) {
                case F32:
                    return io.github.eutro.jwasm.Opcodes.F32;
                case F64:
                    return io.github.eutro.jwasm.Opcodes.F64;
                case I32_8:
                case I32_16:
                case I32:
                    return io.github.eutro.jwasm.Opcodes.I32;
                case I64_8:
                case I64_16:
                case I64_32:
                case I64:
                    return io.github.eutro.jwasm.Opcodes.I64;
                default:
                    throw new IllegalArgumentException();
            }
        }

        StoreType(int ext, String name, String desc) {
            this(name, desc);
            insns.insert(new InsnNode(ext));
        }

        public static StoreType fromOpcode(byte opcode) {
            switch (opcode) {
                // @formatter:off
                case I32_STORE: return I32;
                case I64_STORE: return I64;
                case F32_STORE: return F32;
                case F64_STORE: return F64;
                case I32_STORE8: return I32_8;
                case I32_STORE16: return I32_16;
                case I64_STORE8: return I64_8;
                case I64_STORE16: return I64_16;
                case I64_STORE32: return I64_32;
                // @formatter:on
                default:
                    throw new IllegalArgumentException();
            }
        }

        public byte getOpcode() {
            switch (this) {
                case I32: return I32_STORE;
                case I64: return I64_STORE;
                case F32: return F32_STORE;
                case F64: return F64_STORE;
                case I32_8: return I32_STORE8;
                case I32_16: return I32_STORE16;
                case I64_8: return I64_STORE8;
                case I64_16: return I64_STORE16;
                case I64_32: return I64_STORE32;
                default:
                    throw new IllegalStateException();
            }
        }

        StoreType(String name, String desc) {
            insns.add(new MethodInsnNode(
                    Opcodes.INVOKEVIRTUAL,
                    Type.getInternalName(ByteBuffer.class),
                    name,
                    desc,
                    false
            ));
        }
    }

    public static class OperatorType {
        public byte op;
        public int intOp;

        public OperatorType(byte op, int intOp) {
            this.op = op;
            this.intOp = intOp;
        }

        @Override
        public String toString() {
            return Disassembler.getWasmMnemonic(op, intOp);
        }
    }
}

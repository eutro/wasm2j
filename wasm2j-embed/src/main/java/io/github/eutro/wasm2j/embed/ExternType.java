package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.Opcodes;
import io.github.eutro.jwasm.sexp.Unparser;
import io.github.eutro.jwasm.tree.*;
import io.github.eutro.wasm2j.conf.impl.BasicCallingConvention;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Locale;
import java.util.Objects;

public interface ExternType {
    boolean assignableFrom(ExternType other);

    Kind getKind();

    static ExternType getLocal(ModuleNode module, Kind kind, int index) {
        switch (kind) {
            case FUNC:
                return new Func(index, module);
            case TABLE:
                return new Table(index, module);
            case MEM:
                return new Mem(index, module);
            case GLOBAL:
                return new Global(index, module);
            default:
                throw new AssertionError();
        }
    }

    static ExternType fromImport(AbstractImportNode in, ModuleNode module) {
        Kind kind = Kind.fromByte(in.importType());
        switch (kind) {
            case FUNC:
                return new Func((FuncImportNode) in, module);
            case TABLE:
                return new Table((TableImportNode) in);
            case MEM:
                return new Mem((MemImportNode) in);
            case GLOBAL:
                return new Global((GlobalImportNode) in);
            default:
                throw new AssertionError();
        }
    }

    final class Func implements ExternType {
        public @Nullable MethodType type;
        public final byte @Nullable [] params, results;

        public Func(byte @NotNull [] params, byte @NotNull [] results) {
            this.type = null;
            this.params = params;
            this.results = results;
        }

        public Func(@NotNull MethodType type) {
            this.type = type;
            this.params = null;
            this.results = null;
        }

        public Func(TypeNode typeNode) {
            this(typeNode.params, typeNode.returns);
        }

        public Func(Void ignored, int type, ModuleNode module) {
            this(Objects.requireNonNull(module.types).types.get(type));
        }

        public Func(FuncImportNode fin, ModuleNode module) {
            this(null, fin.type, module);
        }

        public Func(int index, ModuleNode module) {
            this(null, Objects.requireNonNull(module.funcs).funcs.get(index).type, module);
        }

        @GeneratedAccess
        public static Func parse(String s) {
            String[] halves = s.split(":", 2);
            return new Func(parseTypes(halves[0]), parseTypes(halves[1]));
        }

        private static byte[] parseTypes(String types) {
            byte[] bytes = new byte[types.length() / 2];
            for (int i = 0; i < bytes.length; i += 2) {
                int hi = Character.digit(types.charAt(i), 16);
                int lo = Character.digit(types.charAt(i + 1), 16);
                bytes[i / 2] = (byte) (hi << 4 | lo);
            }
            return bytes;
        }

        public static String encode(TypeNode type) {
            return encodeTypes(type.params) + ":" + encodeTypes(type.returns);
        }

        private static String encodeTypes(byte[] params) {
            Formatter fmt = new Formatter(Locale.ROOT);
            for (byte param : params) {
                fmt.format("%02x", param);
            }
            return fmt.toString();
        }

        public MethodType getMethodType() {
            if (type != null) return type;
            assert params != null && results != null;
            return type = MethodType.fromMethodDescriptorString(
                    BasicCallingConvention.methodDesc(params, results).getDescriptor(),
                    getClass().getClassLoader()
            );
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Func func = (Func) o;
            return Arrays.equals(params, func.params) && Arrays.equals(results, func.results);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(params);
            result = 31 * result + Arrays.hashCode(results);
            return result;
        }

        @Override
        public String toString() {
            if (params == null || results == null) return Objects.requireNonNull(type).toString();
            StringBuilder sb = new StringBuilder("(func (params");
            for (byte param : params) sb.append(' ').append(Unparser.unparseType(param));
            sb.append(") (results");
            for (byte result : results) sb.append(' ').append(Unparser.unparseType(result));
            return sb.append("))").toString();
        }

        @Override
        public boolean assignableFrom(ExternType other) {
            return other == null
                    || (other instanceof Func && ((Func) other).params == null)
                    || equals(other);
        }

        @Override
        public Kind getKind() {
            return Kind.FUNC;
        }
    }

    class Table implements ExternType {
        @NotNull
        public final Limits limits;
        public final byte refType;

        public Table(@NotNull Limits limits, byte refType) {
            this.limits = limits;
            this.refType = refType;
        }

        public Table(TableImportNode in) {
            this(new Limits(in.limits), in.type);
        }

        public Table(int index, ModuleNode module) {
            this(Objects.requireNonNull(module.tables).tables.get(index));
        }

        public Table(TableNode tableNode) {
            this(new Limits(tableNode.limits), tableNode.type);
        }

        @GeneratedAccess
        public static ExternType.Table create(int min, @Nullable Integer max, byte type) {
            return new Table(new Limits(min, max), type);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Table table = (Table) o;
            return refType == table.refType && limits.equals(table.limits);
        }

        @Override
        public int hashCode() {
            return Objects.hash(limits, refType);
        }

        @Override
        public boolean assignableFrom(ExternType other) {
            if (other == null) return true;
            if (!(other instanceof Table)) return false;
            Table oTable = (Table) other;
            return refType == oTable.refType
                    && limits.assignableFrom(oTable.limits);
        }

        @Override
        public Kind getKind() {
            return Kind.TABLE;
        }

        @Override
        public String toString() {
            return limits + " " + Unparser.unparseType(refType);
        }
    }

    class Mem implements ExternType {
        @NotNull
        public final Limits limits;

        public Mem(@NotNull Limits limits) {
            this.limits = limits;
        }

        public Mem(MemImportNode in) {
            this(new Limits(in.limits));
        }

        public Mem(int index, ModuleNode module) {
            this(new Limits(Objects.requireNonNull(module.mems).memories.get(index).limits));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Mem mem = (Mem) o;
            return limits.equals(mem.limits);
        }

        @Override
        public int hashCode() {
            return Objects.hash(limits);
        }

        @Override
        public String toString() {
            return limits.toString();
        }

        @Override
        public boolean assignableFrom(ExternType other) {
            return other == null
                    || (other instanceof Mem
                    && limits.assignableFrom(((Mem) other).limits));
        }

        @Override
        public Kind getKind() {
            return Kind.MEM;
        }
    }

    class Global implements ExternType {
        public final boolean isMut;
        public final byte type;

        public Global(boolean isMut, byte type) {
            this.isMut = isMut;
            this.type = type;
        }

        public Global(GlobalTypeNode type) {
            this(type.mut == Opcodes.MUT_VAR, type.type);
        }

        public Global(GlobalImportNode in) {
            this(in.type);
        }

        public Global(int index, ModuleNode module) {
            this(Objects.requireNonNull(module.globals).globals.get(index).type);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Global global = (Global) o;
            return isMut == global.isMut && type == global.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(isMut, type);
        }

        @Override
        public String toString() {
            return (isMut ? "mut" : "const") + " " + Unparser.unparseType(type);
        }

        @Override
        public boolean assignableFrom(ExternType other) {
            return other == null || equals(other);
        }

        @Override
        public Kind getKind() {
            return Kind.GLOBAL;
        }
    }

    final class Limits {
        public final int min;
        public final @Nullable Integer max;

        public Limits(int min, @Nullable Integer max) {
            this.min = min;
            this.max = max;
        }

        public Limits(io.github.eutro.jwasm.Limits limits) {
            this(limits.min, limits.max);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Limits limits = (Limits) o;
            return min == limits.min && Objects.equals(max, limits.max);
        }

        @Override
        public int hashCode() {
            return Objects.hash(min, max);
        }

        public boolean assignableFrom(Limits other) {
            return Integer.compareUnsigned(other.min, min) >= 0
                    && (max == null
                    || (other.max != null && Integer.compareUnsigned(other.max, max) <= 0));
        }

        @Override
        public String toString() {
            return "{" +
                    "min " + Integer.toUnsignedString(min) +
                    ", max " + (max == null ? "none" : Integer.toUnsignedString(max)) +
                    '}';
        }
    }

    enum Kind {
        FUNC,
        TABLE,
        MEM,
        GLOBAL,
        ;

        public static Kind fromByte(byte id) {
            switch (id) {
                case Opcodes.IMPORTS_FUNC:
                    return FUNC;
                case Opcodes.IMPORTS_TABLE:
                    return TABLE;
                case Opcodes.IMPORTS_MEM:
                    return MEM;
                case Opcodes.IMPORTS_GLOBAL:
                    return GLOBAL;
                default:
                    throw new IllegalArgumentException();
            }
        }
    }
}

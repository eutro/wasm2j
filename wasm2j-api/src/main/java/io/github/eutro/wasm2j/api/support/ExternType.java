package io.github.eutro.wasm2j.api.support;

import io.github.eutro.jwasm.Opcodes;
import io.github.eutro.jwasm.sexp.Unparser;
import io.github.eutro.jwasm.tree.*;
import io.github.eutro.wasm2j.core.conf.impl.BasicCallingConvention;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.Formatter;
import java.util.Locale;
import java.util.Objects;

/**
 * The type of a WebAssembly extern.
 */
public interface ExternType {
    /**
     * Return whether this type is assignable from (is a supertype of) the other, by
     * <a href="https://webassembly.github.io/spec/core/valid/types.html#import-subtyping">import subtyping</a>.
     * <p>
     * This formally returns {@code this >= other}, for the subtyping relation {@code >=}.
     *
     * @param other The other type.
     * @return Whether this type matches (is a supertype of) other.
     */
    boolean assignableFrom(ExternType other);

    /**
     * Returns the most general type which is the subtype of both this and the other,
     * or {@code null} (representing the bottom type) if no such actual type exists.
     * <p>
     * The result of this is guaranteed to be such that {@code this} and {@code other} are both
     * {@link #assignableFrom(ExternType)} the result.
     * <p>
     * Formally, this is the greatest lower bound (infimum) of the subtyping relation.
     *
     * @param other The other type.
     * @return The most general type assignable to both this and other.
     */
    @Nullable
    ExternType intersection(ExternType other);

    /**
     * Returns the {@link #intersection(ExternType)} of the two types,
     * returning null (bottom) if either argument is null.
     *
     * @param lhs The first type.
     * @param rhs The second type.
     * @return The intersection.
     * @see #intersection(ExternType)
     */
    static ExternType intersect(ExternType lhs, ExternType rhs) {
        if (lhs == null) return null;
        return lhs.intersection(rhs);
    }

    /**
     * Get the kind of type this is (func, global, memory, table),
     * or null if it is the {@link Top} type.
     *
     * @return The kind.
     */
    @Nullable
    Kind getKind();

    /**
     * Get the type of the {@code index}th local entity (func, global, memory, table) in the WebAssembly module.
     * <p>
     * The {@code index} here does not refer to the usual WebAssembly indices,
     * which include imports. This excludes imports, and only considers the local entities.
     * Use {@link #fromImport(AbstractImportNode, ModuleNode)} for imported types.
     *
     * @param module The module.
     * @param kind   The kind of entity.
     * @param index  The index of the entity.
     * @return The type of the entity.
     */
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

    /**
     * Get the type of an import.
     *
     * @param in     The import node.
     * @param module The module.
     * @return The type of the import.
     */
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

    /**
     * A function type, consisting of parameter and result types.
     */
    final class Func implements ExternType {
        private @Nullable MethodType type;
        /**
         * The parameter types of the function.
         * <p>
         * Null if the function is from Java with an unspecified type.
         */
        public final byte @Nullable [] params;
        /**
         * The result types of the function.
         * <p>
         * Null if the function is from Java with an unspecified type.
         */
        public final byte @Nullable [] results;

        /**
         * Construct a function type with the given parameter and result types.
         *
         * @param params  The parameter types.
         * @param results The result types.
         */
        public Func(byte @NotNull [] params, byte @NotNull [] results) {
            this.type = null;
            this.params = params;
            this.results = results;
        }

        /**
         * Construct a function type with no specific parameter and result types.
         * <p>
         * For casting purposes, the Java type must still be provided.
         *
         * @param type The java type of the function.
         */
        public Func(@NotNull MethodType type) {
            this.type = type;
            this.params = null;
            this.results = null;
        }

        Func(TypeNode typeNode) {
            this(typeNode.params, typeNode.returns);
        }

        Func(Void ignored, int type, ModuleNode module) {
            this(Objects.requireNonNull(module.types).types.get(type));
        }

        Func(FuncImportNode fin, ModuleNode module) {
            this(null, fin.type, module);
        }

        Func(int index, ModuleNode module) {
            this(null, Objects.requireNonNull(module.funcs).funcs.get(index).type, module);
        }

        /**
         * Parse a function type from an {@link #encode(TypeNode) encoded} string.
         *
         * @param s The encoded string.
         * @return The type.
         */
        public static Func parse(String s) {
            String[] halves = s.split(":", 2);
            return new Func(parseTypes(halves[0]), parseTypes(halves[1]));
        }

        private static byte[] parseTypes(String types) {
            byte[] bytes = new byte[types.length() / 2];
            for (int i = 0; i < types.length(); i += 2) {
                int hi = Character.digit(types.charAt(i), 16);
                int lo = Character.digit(types.charAt(i + 1), 16);
                bytes[i / 2] = (byte) (hi << 4 | lo);
            }
            return bytes;
        }

        /**
         * Encode a function type as a string.
         *
         * @param type The type.
         * @return The encoded string.
         */
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

        /**
         * Get the Java method type of the function type, for casting.
         *
         * @return The method type.
         */
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

        @Nullable
        @Override
        public Func intersection(ExternType other) {
            if (other == Top.INSTANCE) return this;
            if (other == null) return null;
            if (!(other instanceof Func)) return null;
            Func otherFunc = (Func) other;
            if (params == null) return this;
            if (otherFunc.params == null) return otherFunc;
            if (!equals(otherFunc)) return null;
            return this;
        }

        /**
         * {@inheritDoc}
         *
         * @return {@link Kind#FUNC}
         */
        @Override
        public Kind getKind() {
            return Kind.FUNC;
        }
    }

    /**
     * A table type, consisting of limits and a component type.
     */
    class Table implements ExternType {
        /**
         * The limits of the table.
         */
        @NotNull
        public final Limits limits;
        /**
         * The type of elements of the table.
         */
        @NotNull
        public final ValType componentType;

        /**
         * Construct a table with the given limits and component type.
         *
         * @param limits        The limits
         * @param componentType The component type.
         */
        public Table(@NotNull Limits limits, @NotNull ValType componentType) {
            this.limits = limits;
            this.componentType = componentType;
        }

        Table(TableImportNode in) {
            this(new Limits(in.limits), ValType.fromOpcode(in.type));
        }

        Table(int index, ModuleNode module) {
            this(Objects.requireNonNull(module.tables).tables.get(index));
        }

        Table(TableNode tableNode) {
            this(new Limits(tableNode.limits), ValType.fromOpcode(tableNode.type));
        }

        /**
         * Create a table with the given limits and type.
         *
         * @param min  The lower limit.
         * @param max  The upper limit, if any.
         * @param type The element type, as a byte.
         * @return The table type.
         */
        public static ExternType.Table create(int min, @Nullable Integer max, byte type) {
            return new Table(new Limits(min, max), ValType.fromOpcode(type));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Table table = (Table) o;
            return componentType == table.componentType && limits.equals(table.limits);
        }

        @Override
        public int hashCode() {
            return Objects.hash(limits, componentType);
        }

        @Override
        public boolean assignableFrom(ExternType other) {
            if (other == null) return true;
            if (!(other instanceof Table)) return false;
            Table oTable = (Table) other;
            return componentType == oTable.componentType
                    && limits.assignableFrom(oTable.limits);
        }

        @Nullable
        @Override
        public Table intersection(ExternType other) {
            if (other == Top.INSTANCE) return this;
            if (other == null) return null;
            if (!(other instanceof Table)) return null;
            Table oTable = (Table) other;
            if (oTable.componentType != componentType) return null;
            Limits newLimits = oTable.limits.intersection(limits);
            if (newLimits == null) return null;
            return new Table(newLimits, componentType);
        }

        /**
         * {@inheritDoc}
         *
         * @return {@link Kind#TABLE}
         */
        @Override
        public Kind getKind() {
            return Kind.TABLE;
        }

        @Override
        public String toString() {
            return limits + " " + componentType;
        }
    }

    /**
     * A memory type, consisting of limits.
     */
    class Mem implements ExternType {
        /**
         * The limits of the memory, in pages.
         */
        @NotNull
        public final Limits limits;

        /**
         * Construct a memory type with the given limits.
         *
         * @param limits The limits.
         */
        public Mem(@NotNull Limits limits) {
            this.limits = limits;
        }

        Mem(MemImportNode in) {
            this(new Limits(in.limits));
        }

        Mem(int index, ModuleNode module) {
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

        @Nullable
        @Override
        public Mem intersection(ExternType other) {
            if (other == Top.INSTANCE) return this;
            if (other == null) return null;
            if (!(other instanceof Mem)) return null;
            Mem oMem = (Mem) other;
            Limits newLimits = oMem.limits.intersection(limits);
            if (newLimits == null) return null;
            return new Mem(newLimits);
        }

        /**
         * {@inheritDoc}
         *
         * @return {@link Kind#MEM}
         */
        @Override
        public Kind getKind() {
            return Kind.MEM;
        }
    }

    /**
     * A global type, consisting of a mutability and a value type.
     */
    class Global implements ExternType {
        /**
         * The mutability of the global, whether it can be assigned to.
         */
        public final boolean isMut;
        /**
         * The value type of the global.
         */
        @NotNull
        public final ValType type;

        /**
         * Construct a global type with the given mutability and value type.
         *
         * @param isMut The mutability.
         * @param type  The value type.
         */
        public Global(boolean isMut, @NotNull ValType type) {
            this.isMut = isMut;
            this.type = type;
        }

        Global(GlobalTypeNode type) {
            this(type.mut == Opcodes.MUT_VAR, ValType.fromOpcode(type.type));
        }

        Global(GlobalImportNode in) {
            this(in.type);
        }

        Global(int index, ModuleNode module) {
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
            return (isMut ? "mut" : "const") + " " + type;
        }

        @Override
        public boolean assignableFrom(ExternType other) {
            return other == null || equals(other);
        }

        @Nullable
        @Override
        public Global intersection(ExternType other) {
            if (other == Top.INSTANCE) return this;
            if (other == null) return null;
            if (!equals(other)) return null;
            return this;
        }

        /**
         * {@inheritDoc}
         *
         * @return {@link Kind#GLOBAL}
         */
        @Override
        public Kind getKind() {
            return Kind.GLOBAL;
        }
    }

    /**
     * A set of limits, a minimum and (possibly) a maximum.
     * <p>
     * These should be considered unsigned.
     */
    final class Limits {
        /**
         * The minimum value.
         */
        public final int min;
        /**
         * The maximum value, or null if unbounded.
         */
        public final @Nullable Integer max;

        /**
         * Construct a set of limits with the given minimum and maximum.
         *
         * @param min The minimum.
         * @param max The maximum.
         */
        public Limits(int min, @Nullable Integer max) {
            this.min = min;
            this.max = max;
        }

        /**
         * Construct a set of limits from JWasm limits, representing the same thing.
         *
         * @param limits The limits.
         */
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

        /**
         * The intersection of these limits with another, or null if they are disjoint.
         *
         * @param other The other limits.
         * @return The intersection.
         */
        @Nullable
        public Limits intersection(Limits other) {
            int min = Integer.compareUnsigned(other.min, this.min) >= 0 ? other.min : this.min;
            Integer max = other.max == null ? this.max : this.max == null ? other.max :
                    Integer.compareUnsigned(other.max, this.max) <= 0 ? other.max : this.max;
            if (max != null && min > max) return null;
            return new Limits(min, max);
        }
    }

    /**
     * The top type. This is the identity of {@link #intersection(ExternType)}.
     * <p>
     * Every func, global, memory and table is an instance of this type,
     * but nothing has this as its only type. The only purpose of this is to
     * serve as the identity of intersection.
     */
    final class Top implements ExternType {
        /**
         * The singleton instance of the top type.
         */
        public static Top INSTANCE = new Top();

        private Top() {
        }

        @Override
        public boolean assignableFrom(ExternType other) {
            return true;
        }

        @Override
        public ExternType intersection(ExternType other) {
            return other;
        }

        /**
         * {@inheritDoc}
         *
         * @return {@code null}
         */
        @Nullable
        @Override
        public Kind getKind() {
            return null;
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

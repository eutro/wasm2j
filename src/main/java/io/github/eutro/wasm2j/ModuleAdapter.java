package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.ModuleVisitor;
import io.github.eutro.jwasm.*;
import io.github.eutro.jwasm.tree.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.ByteBuffer;
import java.util.*;

import static io.github.eutro.jwasm.Opcodes.*;
import static org.objectweb.asm.Opcodes.*;

/**
 * An {@link ModuleVisitor} that adapts a WebAssembly module into a Java class,
 * such that it can {@link #accept} an ObjectWeb ASM {@link ClassVisitor}.
 */
public class ModuleAdapter extends ModuleVisitor {
    protected ClassNode cn = new ClassNode();

    protected final @NotNull TypesNode types = new TypesNode();
    protected final @NotNull ElementSegmentsNode elems = new ElementSegmentsNode();
    protected final @NotNull DataSegmentsNode datas = new DataSegmentsNode();

    protected final Externs externs = new Externs();

    protected final List<MethodNode> funcs = new ArrayList<>();
    protected final List<TypeNode> funcTypes = new ArrayList<>();

    protected final List<FieldNode> mems = new ArrayList<>();
    protected final List<Limits> memLimits = new ArrayList<>();

    protected final List<FieldNode> globals = new ArrayList<>();
    protected final List<ExprNode> globalInits = new ArrayList<>();

    protected final List<FieldNode> tables = new ArrayList<>();
    protected final List<TableNode> tableNodes = new ArrayList<>();

    protected @Nullable Integer start;

    public ModuleAdapter(String internalName) {
        cn.name = internalName;
        cn.version = V1_8;
        cn.access = ACC_PUBLIC | ACC_SUPER;
        cn.superName = "java/lang/Object";
    }

    @Override
    public void visitCustom(@NotNull String name, byte @NotNull [] data) {
        AnnotationVisitor av = cn.visitAnnotation("Lio/github/eutro/wasm2j/runtime/CustomSection;", true);
        av.visit("name", name);
        av.visit("data", data);
    }

    @Override
    public @Nullable TypesVisitor visitTypes() {
        return types;
    }

    @Override
    public @Nullable ImportsVisitor visitImports() {
        return new ImportsVisitor(super.visitImports()) {
            @Override
            public void visitFuncImport(@NotNull String module, @NotNull String name, int type) {
                throw new UnsupportedOperationException(String.format("Func import %s from %s not supported", module, name));
            }

            @Override
            public void visitTableImport(@NotNull String module, @NotNull String name, int min, @Nullable Integer max, byte type) {
                throw new UnsupportedOperationException(String.format("Table import %s from %s not supported", module, name));
            }

            @Override
            public void visitMemImport(@NotNull String module, @NotNull String name, int min, @Nullable Integer max) {
                throw new UnsupportedOperationException(String.format("Memory import %s from %s not supported", module, name));
            }

            @Override
            public void visitGlobalImport(@NotNull String module, @NotNull String name, byte mut, byte type) {
                throw new UnsupportedOperationException(String.format("Global import %s from %s not supported", module, name));
            }
        };
    }

    @Override
    public @Nullable FunctionsVisitor visitFuncs() {
        return new FunctionsVisitor() {
            @Override
            public void visitFunc(int type) {
                MethodNode mn = new MethodNode();
                mn.access = ACC_PRIVATE;
                TypeNode funcType = getType(type);
                mn.desc = Types.methodDesc(funcType).toString();
                mn.name = "func" + funcs.size();
                funcs.add(mn);
                funcTypes.add(funcType);
                cn.methods.add(mn);
                externs.funcs.add(new FuncExtern.ModuleFuncExtern(cn, mn, funcType));
            }
        };
    }

    @Override
    public @Nullable TablesVisitor visitTables() {
        return new TablesVisitor() {
            @Override
            public void visitTable(int min, @Nullable Integer max, byte type) {
                FieldNode fn = new FieldNode(ACC_PRIVATE,
                        "table" + tables.size(),
                        "[" + Types.toJava(type).getDescriptor(),
                        null,
                        null);
                fn.visitAnnotation("Lio/github/eutro/wasm2j/runtime/Table;", true);
                tables.add(fn);
                tableNodes.add(new TableNode(new Limits(min, max), type));
                cn.fields.add(fn);
                externs.tables.add(new TypedExtern.ModuleTypedExtern(fn, cn.name, type));
            }
        };
    }

    @Override
    public @Nullable MemoriesVisitor visitMems() {
        return new MemoriesVisitor() {
            @Override
            public void visitMemory(int min, @Nullable Integer max) {
                FieldNode fn = new FieldNode(ACC_PRIVATE,
                        "mem" + mems.size(),
                        Type.getType(ByteBuffer.class).toString(),
                        null,
                        null);
                AnnotationVisitor av = fn.visitAnnotation("Lio/github/eutro/wasm2j/runtime/LinearMemory;", true);
                av.visit("min", min);
                if (max != null) av.visit("max", max);
                mems.add(fn);
                memLimits.add(new Limits(min, max));
                cn.fields.add(fn);
                externs.mems.add(new Extern.ModuleFieldExtern(fn, cn.name));
            }
        };
    }

    @Override
    public @Nullable GlobalsVisitor visitGlobals() {
        return new GlobalsVisitor() {
            @Override
            public @NotNull ExprVisitor visitGlobal(byte mut, byte type) {
                FieldNode fn = new FieldNode(ACC_PRIVATE,
                        "glob" + globals.size(),
                        Types.toJava(type).getDescriptor(),
                        null,
                        null);
                fn.visitAnnotation("Lio/github/eutro/wasm2j/runtime/Global;", true);
                if (mut == MUT_CONST) fn.access |= ACC_FINAL;
                globals.add(fn);
                cn.fields.add(fn);
                externs.globals.add(new TypedExtern.ModuleTypedExtern(fn, cn.name, type));
                ExprNode init = new ExprNode();
                globalInits.add(init);
                return init;
            }
        };
    }

    @Override
    public @Nullable ExportsVisitor visitExports() {
        int inFuncsC = externs.funcs.size() - funcs.size();
        int inMemsC = externs.mems.size() - mems.size();
        int inGlobalsC = externs.globals.size() - globals.size();
        int inTablesC = externs.tables.size() - tables.size();
        return new ExportsVisitor() {
            @Override
            public void visitExport(@NotNull String name, byte type, int index) {
                switch (type) {
                    case EXPORTS_FUNC:
                        MethodNode method = funcs.get(index - inFuncsC);
                        method.name = name;
                        method.access &= ~ACC_PRIVATE;
                        method.access |= ACC_PUBLIC;
                        break;
                    case EXPORTS_TABLE:
                        FieldNode table = tables.get(index - inTablesC);
                        table.name = name;
                        table.access &= ~ACC_PRIVATE;
                        table.access |= ACC_PUBLIC;
                        break;
                    case EXPORTS_MEM:
                        FieldNode mem = mems.get(index - inMemsC);
                        mem.name = name;
                        mem.access &= ~ACC_PRIVATE;
                        mem.access |= ACC_PUBLIC;
                        break;
                    case EXPORTS_GLOBAL:
                        FieldNode global = globals.get(index - inGlobalsC);
                        global.name = name;
                        global.access &= ~ACC_PRIVATE;
                        global.access |= ACC_PUBLIC;
                        break;
                }
            }
        };
    }

    @Override
    public @Nullable ElementSegmentsVisitor visitElems() {
        return elems;
    }

    @Override
    public @Nullable DataSegmentsVisitor visitDatas() {
        return datas;
    }

    @Override
    public @Nullable CodesVisitor visitCode() {
        return new CodesVisitor() {
            int ci = 0;

            @Override
            public @NotNull ExprVisitor visitCode(byte @NotNull [] locals) {
                ExprNode expr = new ExprNode();
                return new ExprVisitor(expr) {
                    @Override
                    public void visitEnd() {
                        putBody(funcTypes.get(ci), funcs.get(ci), locals, expr);
                        ++ci;
                    }
                };
            }
        };
    }

    @Override
    public void visitStart(int func) {
        start = func;
    }

    @Override
    public void visitEnd() {
        cn.methods.add(createConstructor());
    }

    protected TypeNode getType(int index) {
        return getTypes().get(index);
    }

    @NotNull
    protected List<TypeNode> getTypes() {
        return types.types == null ? Collections.emptyList() : types.types;
    }

    public void accept(ClassVisitor cv) {
        cn.accept(cv);
    }

    @NotNull
    private MethodNode createConstructor() {
        MethodNode mn;
        mn = new MethodNode();
        mn.access = ACC_PUBLIC;
        mn.name = "<init>";
        mn.desc = "()V";
        FieldNode[] passiveElems = elems.elems != null ? new FieldNode[elems.elems.size()] : new FieldNode[0];
        FieldNode[] passiveDatas = datas.datas != null ? new FieldNode[datas.datas.size()] : new FieldNode[0];
        Context ctx = new Context(
                new JumpTrackingVisitor(cn.name, mn.access, mn.name, mn.desc, mn),
                mn.access,
                mn.name,
                mn.desc,
                getTypes(),
                new TypeNode(new byte[0], new byte[0]),
                externs,
                new int[0],
                new Type[0],
                passiveElems,
                passiveDatas);

        ctx.visitVarInsn(ALOAD, 0);
        ctx.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);

        {
            int mi = 0;
            for (Limits limits : memLimits) {
                ctx.visitVarInsn(ALOAD, 0);
                ctx.visitLdcInsn(limits.min * PAGE_SIZE);
                ctx.visitMethodInsn(INVOKESTATIC,
                        "java/nio/ByteBuffer",
                        "allocateDirect",
                        "(I)Ljava/nio/ByteBuffer;",
                        false);
                ctx.visitFieldInsn(GETSTATIC,
                        "java/nio/ByteOrder",
                        "LITTLE_ENDIAN",
                        "Ljava/nio/ByteOrder;");
                ctx.visitMethodInsn(INVOKEVIRTUAL,
                        "java/nio/ByteBuffer",
                        "order",
                        "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;",
                        false);
                ctx.visitFieldInsn(PUTFIELD, cn.name, mems.get(mi).name, Type.getDescriptor(ByteBuffer.class));
                ++mi;
            }
        }

        {
            int ti = 0;
            for (TableNode table : tableNodes) {
                ctx.loadThis();
                ctx.push(table.limits.min);
                ctx.newArray(Types.toJava(table.type));
                FieldNode fn = tables.get(ti);
                ctx.visitFieldInsn(PUTFIELD, cn.name, fn.name, fn.desc);
                ++ti;
            }
        }

        {
            int gi = 0;
            for (ExprNode init : globalInits) {
                ctx.loadThis();
                ExprAdapter.translateInto(init, ctx);
                FieldNode fn = globals.get(gi);
                ctx.visitFieldInsn(PUTFIELD, cn.name, fn.name, fn.desc);
                ++gi;
            }
        }

        if (elems.elems != null) {
            int[] actives = new int[elems.elems.size()];
            int ei = 0;
            for (ElementNode elem : elems) {
                Type elemType = Types.toJava(elem.type);
                if (elem.offset == null && elem.passive) {
                    ctx.loadThis();
                }
                ctx.push(elem.size());
                ctx.newArray(elemType);
                int i = 0;
                for (ExprNode init : elem) {
                    ctx.dup();
                    ctx.push(i);
                    ExprAdapter.translateInto(init, ctx);
                    ctx.arrayStore(elemType);
                    ++i;
                }
                if (elem.offset != null) {
                    ctx.storeLocal(actives[ei] = ctx.newLocal(Type.getType("[" + elemType)));
                } else if (elem.passive) {
                    FieldNode fn = passiveElems[ei] = new FieldNode(ACC_PRIVATE,
                            "elem" + ei,
                            "[" + elemType,
                            null,
                            null);
                    cn.fields.add(fn);
                    ctx.visitFieldInsn(PUTFIELD, cn.name, fn.name, fn.desc);
                } else {
                    ctx.pop();
                }
                ++ei;
            }

            ei = 0;
            for (ElementNode elem : elems) {
                if (elem.offset != null) {
                    if (elem.table != 0) throw new IllegalStateException();
                    TypedExtern table = externs.tables.get(elem.table);
                    ctx.loadLocal(actives[ei]);
                    ctx.push(0);
                    table.emitGet(ctx);
                    ExprAdapter.translateInto(elem.offset, ctx);
                    ctx.push(elem.size());
                    ctx.visitMethodInsn(INVOKESTATIC, "java/lang/System", "arraycopy",
                            "(Ljava/lang/Object;ILjava/lang/Object;II)V",
                            false);
                    ctx.push((String) null);
                    ctx.storeLocal(actives[ei]);
                }
                ++ei;
            }
        }

        int di = 0;
        for (DataNode data : datas) {
            boolean active = data.offset != null;
            if (active) {
                if (data.memory != 0) throw new IllegalStateException();
                Extern mem = externs.mems.get(0);
                mem.emitGet(ctx);
                ExprAdapter.translateInto(data.offset, ctx);
            } else {
                ctx.loadThis();
            }

            ByteBuffer buf = ByteBuffer.wrap(data.init);
            ctx.push(data.init.length);
            ctx.newArray(Type.BYTE_TYPE);
            if (data.init.length != 0) {
                ctx.dup();
                ctx.visitMethodInsn(INVOKESTATIC, "java/nio/ByteBuffer", "wrap",
                        "([B)Ljava/nio/ByteBuffer;",
                        false);
                int i = 0;
                for (; i + Long.BYTES <= data.init.length; i += Long.BYTES) {
                    ctx.push(buf.getLong());
                    ctx.visitMethodInsn(INVOKEVIRTUAL, "java/nio/ByteBuffer", "putLong",
                            "(J)Ljava/nio/ByteBuffer;",
                            false);
                }
                if (i != data.init.length) {
                    for (; i < data.init.length; i++) {
                        ctx.push(buf.get());
                        ctx.visitMethodInsn(INVOKEVIRTUAL, "java/nio/ByteBuffer", "put",
                                "(B)Ljava/nio/ByteBuffer;",
                                false);
                    }
                }
                ctx.pop();
            }

            if (active) {
                ctx.visitMethodInsn(INVOKEVIRTUAL, "java/nio/ByteBuffer", "put",
                        "(I[B)Ljava/nio/ByteBuffer;",
                        false);
                ctx.pop();
            } else {
                FieldNode fn = passiveDatas[di] = new FieldNode(ACC_PRIVATE, "data" + di, "[B", null, null);
                cn.fields.add(fn);
                ctx.visitFieldInsn(PUTFIELD, cn.name, fn.name, fn.desc);
            }
            ++di;
        }

        if (start != null) {
            FuncExtern startf = externs.funcs.get(start);
            startf.emitInvoke(ctx);
        }
        ctx.visitInsn(Opcodes.RETURN);
        ctx.visitMaxs(/* calculated */ 0, 0);
        return mn;
    }

    protected void putBody(TypeNode funcType, MethodNode method, byte[] locals, ExprNode expr) {
        Label start = new Label();
        Label end = new Label();

        int[] indeces = new int[funcType.params.length + locals.length];
        Type[] types = new Type[indeces.length];
        Context ctx = new Context(
                new JumpTrackingVisitor(cn.name, method.access, method.name, method.desc, method),
                method.access,
                method.name,
                method.desc,
                getTypes(),
                funcType,
                externs,
                indeces,
                types,
                new FieldNode[0],
                new FieldNode[0]);

        int localIndex = 1;
        for (int ai = 0; ai < funcType.params.length; ai++) {
            Type localType = types[ai] = Types.toJava(funcType.params[ai]);
            indeces[ai] = localIndex;
            localIndex += localType.getSize();
        }

        ctx.mark(start);
        for (int li = 0; li < locals.length; li++) {
            byte local = locals[li];
            Type localType = types[funcType.params.length + li] = Types.toJava(local);
            indeces[funcType.params.length + li] = localIndex;
            Util.defaultValue(localType).accept(ctx);
            ctx.visitVarInsn(localType.getOpcode(ISTORE), localIndex);
            localIndex += localType.getSize();
        }
        ExprAdapter.translateInto(expr, ctx);
        ctx.mark(end);
        ctx.compress(funcType.returns);
        ctx.returnValue();
        ctx.visitMaxs(/* calculated */ 0, 0);
    }

}

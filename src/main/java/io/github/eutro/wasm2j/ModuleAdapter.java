package io.github.eutro.wasm2j;

import io.github.eutro.jwasm.ModuleVisitor;
import io.github.eutro.jwasm.tree.*;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static io.github.eutro.jwasm.Opcodes.*;
import static org.objectweb.asm.Opcodes.*;

public class ModuleAdapter extends ModuleVisitor {
    public @NotNull ModuleNode node;

    public ModuleAdapter(@NotNull ModuleNode node) {
        super(node);
        this.node = node;
    }

    public ModuleAdapter() {
        this(new ModuleNode());
    }

    protected TypeNode getType(int index) {
        return getTypes().get(index);
    }

    @NotNull
    protected List<TypeNode> getTypes() {
        return Objects.requireNonNull(Objects.requireNonNull(node.types).types);
    }

    public ClassNode toJava(String internalName) {
        ClassNode cn = new ClassNode();
        cn.version = V1_8;
        cn.access = ACC_PUBLIC | ACC_SUPER;
        cn.name = internalName;
        cn.superName = "java/lang/Object";
        return toJava(cn);
    }

    public ClassNode toJava(ClassNode cn) {
        addCustoms(cn);
        Externs externs = new Externs();
        addImports(externs);
        List<MethodNode> funcs = new ArrayList<>();
        List<FieldNode> mems = new ArrayList<>();
        List<FieldNode> globals = new ArrayList<>();
        List<FieldNode> tables = new ArrayList<>();
        createExterns(cn, externs, funcs, mems, globals, tables);
        cn.methods.add(createConstructor(cn, externs, funcs, mems, globals, tables));
        if (node.codes != null) {
            int ci = 0;
            for (CodeNode code : node.codes) {
                putBody(cn, externs, ci, code, cn.methods.get(ci));
                ++ci;
            }
        }
        return cn;
    }

    private void addCustoms(ClassNode cn) {
        for (List<CustomNode> sections : node.customs) {
            if (sections != null) {
                for (CustomNode section : sections) {
                    AnnotationVisitor av = cn.visitAnnotation("Lio/github/eutro/wasm2j/runtime/CustomSection;", true);
                    av.visit("name", section.name);
                    av.visit("data", section.data);
                }
            }
        }
    }

    protected void addImports(Externs externs) {
        if (node.imports != null) {
            for (AbstractImportNode imprt : node.imports) {
                switch (imprt.importType()) {
                    case IMPORTS_FUNC:
                        externs.funcs.add(resolveFuncImport((FuncImportNode) imprt));
                        break;
                    case IMPORTS_MEM:
                        externs.mems.add(resolveMemImport((MemImportNode) imprt));
                        break;
                    case IMPORTS_GLOBAL:
                        externs.globals.add(resolveGlobalImport((GlobalImportNode) imprt));
                        break;
                    case IMPORTS_TABLE:
                        externs.tables.add(resolveTableImport((TableImportNode) imprt));
                        break;
                }
            }
        }
    }

    protected void createExterns(ClassNode cn,
                                 Externs externs,
                                 List<MethodNode> funcs,
                                 List<FieldNode> mems,
                                 List<FieldNode> globals,
                                 List<FieldNode> tables) {
        int inFuncsC = externs.funcs.size();
        int inMemsC = externs.mems.size();
        int inGlobalsC = externs.globals.size();
        int inTablesC = externs.tables.size();
        if (node.funcs != null) {
            for (FuncNode func : node.funcs) {
                MethodNode mn = new MethodNode();
                mn.access = ACC_PRIVATE;
                TypeNode funcType = getType(func.type);
                mn.desc = Types.methodDesc(funcType).toString();
                mn.name = "func" + cn.methods.size();
                funcs.add(mn);
                cn.methods.add(mn);
                externs.funcs.add(new FuncExtern.ModuleFuncExtern(cn, mn, funcType));
            }
        }
        if (node.mems != null) {
            for (MemoryNode ignored : node.mems) {
                FieldNode fn = new FieldNode(ACC_PRIVATE,
                        "mem" + mems.size(),
                        Type.getType(ByteBuffer.class).toString(),
                        null,
                        null);
                mems.add(fn);
                cn.fields.add(fn);
                externs.mems.add(new Extern.ModuleFieldExtern(fn, cn.name));
            }
        }
        if (node.globals != null) {
            for (GlobalNode global : node.globals) {
                FieldNode fn = new FieldNode(ACC_PRIVATE,
                        "glob" + globals.size(),
                        Types.toJava(global.type.type).getDescriptor(),
                        null,
                        null);
                if (global.type.mut == MUT_CONST) fn.access |= ACC_FINAL;
                globals.add(fn);
                cn.fields.add(fn);
                externs.globals.add(new TypedExtern.ModuleTypedExtern(fn, cn.name, global.type.type));
            }
        }
        if (node.tables != null) {
            for (TableNode table : node.tables) {
                FieldNode fn = new FieldNode(ACC_PRIVATE,
                        "table" + tables.size(),
                        "[" + Types.toJava(table.type).getDescriptor(),
                        null,
                        null);
                tables.add(fn);
                cn.fields.add(fn);
                externs.tables.add(new TypedExtern.ModuleTypedExtern(fn, cn.name, table.type));
            }
        }
        if (node.exports != null) {
            for (ExportNode export : node.exports) {
                switch (export.type) {
                    case EXPORTS_FUNC:
                        MethodNode method = funcs.get(export.index - inFuncsC);
                        method.name = export.name;
                        method.access &= ~ACC_PRIVATE;
                        method.access |= ACC_PUBLIC;
                        break;
                    case EXPORTS_TABLE:
                        FieldNode table = tables.get(export.index = inTablesC);
                        table.name = export.name;
                        table.access &= ~ACC_PRIVATE;
                        table.access |= ACC_PUBLIC;
                        break;
                    case EXPORTS_MEM:
                        FieldNode mem = mems.get(export.index - inMemsC);
                        mem.name = export.name;
                        mem.access &= ~ACC_PRIVATE;
                        mem.access |= ACC_PUBLIC;
                        break;
                    case EXPORTS_GLOBAL:
                        FieldNode global = globals.get(export.index - inGlobalsC);
                        global.name = export.name;
                        global.access &= ~ACC_PRIVATE;
                        global.access |= ACC_PUBLIC;
                        break;
                }
            }
        }
    }

    @NotNull
    private MethodNode createConstructor(ClassNode cn,
                                         Externs externs,
                                         List<MethodNode> funcs,
                                         List<FieldNode> mems,
                                         List<FieldNode> globals,
                                         List<FieldNode> tables) {
        MethodNode mn;
        mn = new MethodNode();
        mn.access = ACC_PUBLIC;
        mn.name = "<init>";
        mn.desc = "()V";
        FieldNode[] passiveElems = (node.elems != null && node.elems.elems != null) ?
                new FieldNode[node.elems.elems.size()] : new FieldNode[0];
        FieldNode[] passiveDatas = (node.datas != null && node.datas.datas != null) ?
                new FieldNode[node.datas.datas.size()] : new FieldNode[0];
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
        if (node.mems != null) {
            int i = 0;
            for (MemoryNode mem : node.mems) {
                ctx.visitVarInsn(ALOAD, 0);
                ctx.visitLdcInsn(mem.limits.min * PAGE_SIZE);
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
                ctx.visitFieldInsn(PUTFIELD, cn.name, mems.get(i).name, Type.getDescriptor(ByteBuffer.class));
                ++i;
            }
        }
        if (node.tables != null) {
            int ti = 0;
            for (TableNode table : node.tables) {
                ctx.loadThis();
                ctx.push(table.limits.min);
                ctx.newArray(Types.toJava(table.type));
                FieldNode fn = tables.get(ti);
                ctx.visitFieldInsn(PUTFIELD, cn.name, fn.name, fn.desc);
                ++ti;
            }
        }

        if (node.globals != null) {
            int i = 0;
            for (GlobalNode global : node.globals) {
                ctx.loadThis();
                ExprAdapter.translateInto(global.init, ctx);
                FieldNode fn = globals.get(i);
                ctx.visitFieldInsn(PUTFIELD, cn.name, fn.name, fn.desc);
                ++i;
            }
        }

        if (node.elems != null && node.elems.elems != null) {
            int[] actives = new int[node.elems.elems.size()];
            int ei = 0;
            for (ElementNode elem : node.elems) {
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
            for (ElementNode elem : node.elems) {
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

        if (node.datas != null && node.datas.datas != null) {
            int di = 0;
            for (DataNode data : node.datas) {
                if (data.offset != null) {
                    if (data.memory != 0) throw new IllegalStateException();
                    Extern mem = externs.mems.get(0);
                    mem.emitGet(ctx);
                    ctx.visitMethodInsn(INVOKEVIRTUAL, "java/nio/ByteBuffer", "duplicate",
                            "()Ljava/nio/ByteBuffer;",
                            false);
                    ExprAdapter.translateInto(data.offset, ctx);
                    ctx.visitMethodInsn(INVOKEVIRTUAL, "java/nio/ByteBuffer", "position",
                            "(I)Ljava/nio/ByteBuffer;",
                            false);
                    for (int i = 0; i < data.init.length; i++) {
                        byte b = data.init[i];
                        ctx.push(b);
                        ctx.visitMethodInsn(INVOKEVIRTUAL, "java/nio/ByteBuffer", "put",
                                "(B)Ljava/nio/ByteBuffer;",
                                false);
                    }
                    ctx.pop();
                } else {
                    FieldNode fn = passiveDatas[di] = new FieldNode(ACC_PRIVATE, "data" + di, "[B", null, null);
                    ctx.loadThis();
                    ctx.newArray(Type.BYTE_TYPE);
                    for (int i = 0; i < data.init.length; i++) {
                        ctx.push(i);
                        ctx.push(data.init[i]);
                        ctx.arrayStore(Type.BYTE_TYPE);
                    }
                    ctx.visitFieldInsn(PUTFIELD, cn.name, fn.name, fn.desc);
                }
                ++di;
            }
        }

        if (node.start != null) {
            ctx.visitVarInsn(ALOAD, 0);
            MethodNode start = funcs.get(node.start);
            if (!"()V".equals(start.desc)) throw new IllegalArgumentException();
            ctx.visitMethodInsn(INVOKEVIRTUAL, cn.name, start.name, start.desc, false);
        }
        ctx.visitInsn(Opcodes.RETURN);
        ctx.visitMaxs(/* calculated */ 0, 0);
        return mn;
    }

    protected void putBody(ClassNode cn,
                           Externs externs,
                           int ci,
                           CodeNode code,
                           MethodNode method) {
        Label start = new Label();
        Label end = new Label();

        TypeNode funcType = getType(Objects.requireNonNull(Objects.requireNonNull(node.funcs).funcs).get(ci).type);

        int[] indeces = new int[funcType.params.length + code.locals.length];
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
            ctx.visitParameter("arg" + ai, 0);
            localIndex += localType.getSize();
        }

        ctx.mark(start);
        for (int li = 0; li < code.locals.length; li++) {
            byte local = code.locals[li];
            Type localType = types[funcType.params.length + li] = Types.toJava(local);
            indeces[funcType.params.length + li] = localIndex;
            ctx.visitLocalVariable("loc" + li, localType.getDescriptor(), null, start, end, localIndex);
            Util.defaultValue(localType).accept(ctx);
            ctx.visitVarInsn(localType.getOpcode(ISTORE), localIndex);
            localIndex += localType.getSize();
        }
        ExprAdapter.translateInto(code.expr, ctx);
        ctx.mark(end);
        ctx.compress(funcType.returns);
        ctx.returnValue();
        ctx.visitMaxs(/* calculated */ 0, 0);
    }

    protected @NotNull FuncExtern resolveFuncImport(FuncImportNode imprt) {
        throw new UnsupportedOperationException("Func imports not supported");
    }

    protected @NotNull TypedExtern resolveTableImport(TableImportNode imprt) {
        throw new UnsupportedOperationException("Table imports not supported");
    }

    protected @NotNull Extern resolveMemImport(MemImportNode imprt) {
        throw new UnsupportedOperationException("Memory imports not supported");
    }

    protected @NotNull TypedExtern resolveGlobalImport(GlobalImportNode imprt) {
        throw new UnsupportedOperationException("Global imports not supported");
    }

}

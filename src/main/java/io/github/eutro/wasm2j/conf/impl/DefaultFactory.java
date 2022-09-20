package io.github.eutro.wasm2j.conf.impl;

import io.github.eutro.jwasm.tree.*;
import io.github.eutro.wasm2j.conf.Conventions;
import io.github.eutro.wasm2j.conf.api.*;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ext.WasmExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.util.IRUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.TypeInsnNode;

import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

import static io.github.eutro.jwasm.Opcodes.*;
import static io.github.eutro.wasm2j.conf.Getters.GET_THIS;
import static io.github.eutro.wasm2j.conf.Getters.fieldGetter;

public class DefaultFactory implements WirJavaConventionFactory {
    final CallingConvention callingConvention;
    final ImportFactory<FuncImportNode, FunctionConvention> functionImports;
    final ImportFactory<GlobalImportNode, GlobalConvention> globalImports;
    final ImportFactory<MemImportNode, MemoryConvention> memoryImports;
    final ImportFactory<TableImportNode, TableConvention> tableImports;

    DefaultFactory(
            CallingConvention callingConvention,
            ImportFactory<FuncImportNode, FunctionConvention> functionImports,
            ImportFactory<GlobalImportNode, GlobalConvention> globalImports,
            ImportFactory<MemImportNode, MemoryConvention> memoryImports,
            ImportFactory<TableImportNode, TableConvention> tableImports
    ) {
        this.callingConvention = callingConvention;
        this.functionImports = functionImports;
        this.globalImports = globalImports;
        this.memoryImports = memoryImports;
        this.tableImports = tableImports;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ImportFactory<FuncImportNode, FunctionConvention> functionImports = Builder.unsupported();

        private ImportFactory<GlobalImportNode, GlobalConvention> globalImports = Builder.unsupported();

        private ImportFactory<MemImportNode, MemoryConvention> memoryImports = Builder.unsupported();
        private ImportFactory<TableImportNode, TableConvention> tableImports = Builder.unsupported();

        private CallingConvention callingConvention = Conventions.DEFAULT_CC;

        private static <Import extends AbstractImportNode, Convention>
        ImportFactory<Import, Convention> unsupported() {
            return (m, e) -> {
                throw new UnsupportedOperationException();
            };
        }

        public Builder setFunctionImports(ImportFactory<FuncImportNode, FunctionConvention> factory) {
            functionImports = factory;
            return this;
        }

        public Builder setGlobalImports(ImportFactory<GlobalImportNode, GlobalConvention> factory) {
            globalImports = factory;
            return this;
        }

        public Builder setMemoryImports(ImportFactory<MemImportNode, MemoryConvention> factory) {
            memoryImports = factory;
            return this;
        }

        public Builder setTableImports(ImportFactory<TableImportNode, TableConvention> factory) {
            tableImports = factory;
            return this;
        }

        public Builder setCallingConvention(CallingConvention callingConvention) {
            this.callingConvention = callingConvention;
            return this;
        }

        public DefaultFactory build() {
            return new DefaultFactory(
                    callingConvention,
                    functionImports,
                    globalImports,
                    memoryImports,
                    tableImports
            );
        }
    }

    @Override
    public WirJavaConvention create(Module module) {
        JavaExts.JavaClass jClass = new JavaExts.JavaClass("com/example/FIXME");
        module.attachExt(JavaExts.JAVA_CLASS, jClass);

        List<FunctionConvention> funcs = new ArrayList<>();
        List<GlobalConvention> globals = new ArrayList<>();
        List<MemoryConvention> memories = new ArrayList<>();
        List<TableConvention> tables = new ArrayList<>();

        List<JavaExts.JavaField> lGlobals = new ArrayList<>();
        List<JavaExts.JavaField> lMemories = new ArrayList<>();
        List<JavaExts.JavaField> lTables = new ArrayList<>();

        return new DefaultWirJavaConvention(module,
                funcs,
                globals,
                lGlobals,
                memories,
                lMemories,
                tables,
                lTables,
                jClass
        );
    }

    public class DefaultWirJavaConvention implements WirJavaConvention {
        private final Module module;
        private final List<FunctionConvention> funcs;
        private final List<GlobalConvention> globals;
        private final List<JavaExts.JavaField> lGlobals;
        private final List<MemoryConvention> memories;
        private final List<JavaExts.JavaField> lMemories;
        private final List<TableConvention> tables;
        private final List<JavaExts.JavaField> lTables;
        private final JavaExts.JavaClass jClass;

        public DefaultWirJavaConvention(
                Module module,
                List<FunctionConvention> funcs,
                List<GlobalConvention> globals,
                List<JavaExts.JavaField> lGlobals,
                List<MemoryConvention> memories,
                List<JavaExts.JavaField> lMemories,
                List<TableConvention> tables,
                List<JavaExts.JavaField> lTables,
                JavaExts.JavaClass jClass
        ) {
            this.module = module;
            this.funcs = funcs;
            this.globals = globals;
            this.lGlobals = lGlobals;
            this.memories = memories;
            this.lMemories = lMemories;
            this.tables = tables;
            this.lTables = lTables;
            this.jClass = jClass;
        }

        protected CallingConvention getCC() {
            return callingConvention;
        }

        protected FunctionConvention getImport(FuncImportNode funcImport) {
            return functionImports.createImport(module, funcImport);
        }

        protected MemoryConvention getImport(MemImportNode memImport) {
            return memoryImports.createImport(module, memImport);
        }

        protected TableConvention getImport(TableImportNode tableImport) {
            return tableImports.createImport(module, tableImport);
        }

        protected GlobalConvention getImport(GlobalImportNode globalImport) {
            return globalImports.createImport(module, globalImport);
        }

        @Override
        public void preEmit() {
            ModuleNode node = module.getExtOrThrow(WasmExts.MODULE);
            Map<ExprNode, Function> funcMap = module.getExtOrThrow(WasmExts.FUNC_MAP);

            if (node.imports != null && node.imports.imports != null) {
                for (AbstractImportNode importNode : node.imports) {
                    // @formatter:off
                    switch (importNode.importType()) {
                        case IMPORTS_FUNC: funcs.add(getImport((FuncImportNode) importNode)); break;
                        case IMPORTS_GLOBAL: globals.add(getImport((GlobalImportNode) importNode)); lGlobals.add(null); break;
                        case IMPORTS_MEM: memories.add(getImport((MemImportNode) importNode)); lMemories.add(null); break;
                        case IMPORTS_TABLE: tables.add(getImport((TableImportNode) importNode)); lTables.add(null); break;
                        default: throw new AssertionError();
                    }
                    // @formatter:on
                }
            }

            if (node.funcs != null && node.funcs.funcs != null) {
                assert node.types != null && node.types.types != null;
                assert node.codes != null && node.codes.codes != null;
                int i = 0;
                Iterator<CodeNode> it = node.codes.codes.iterator();
                for (FuncNode fn : node.funcs) {
                    TypeNode typeNode = node.types.types.get(fn.type);
                    JavaExts.JavaMethod method = new JavaExts.JavaMethod(
                            jClass,
                            "func" + i++,
                            getCC().getDescriptor(typeNode).getDescriptor(),
                            JavaExts.JavaMethod.Type.FINAL
                    );
                    jClass.methods.add(method);
                    funcs.add(new InstanceFunctionConvention(
                            ExportableConvention.methodExporter(method),
                            GET_THIS,
                            method,
                            getCC()
                    ));
                    Function implFunc = funcMap.get(it.next().expr);
                    method.attachExt(JavaExts.METHOD_IMPL, implFunc);
                    implFunc.attachExt(JavaExts.FUNCTION_DESCRIPTOR, method.descriptor);
                    implFunc.attachExt(JavaExts.FUNCTION_OWNER, jClass);
                }
            }

            if (node.globals != null) {
                int i = 0;
                for (GlobalNode global : node.globals) {
                    JavaExts.JavaField field = new JavaExts.JavaField(
                            jClass,
                            "global" + i++,
                            BasicCallingConvention.javaType(global.type.type).getDescriptor(),
                            false
                    );
                    jClass.fields.add(field);
                    globals.add(new FieldGlobalConvention(
                            ExportableConvention.fieldExporter(field),
                            GET_THIS,
                            field
                    ));
                    lGlobals.add(field);
                }
            }

            if (node.mems != null) {
                int i = 0;
                for (MemoryNode ignored : node.mems) {
                    JavaExts.JavaField field = new JavaExts.JavaField(
                            jClass,
                            "mem" + i++,
                            Type.getDescriptor(ByteBuffer.class),
                            false
                    );
                    jClass.fields.add(field);
                    memories.add(new ByteBufferMemoryConvention(
                            ExportableConvention.fieldExporter(field),
                            fieldGetter(GET_THIS, field)
                    ));
                    lMemories.add(field);
                }
            }

            if (node.tables != null) {
                int i = 0;
                for (TableNode table : node.tables) {
                    JavaExts.JavaField field = new JavaExts.JavaField(
                            jClass,
                            "table" + i++,
                            "[" + BasicCallingConvention.javaType(table.type).getDescriptor(),
                            false
                    );
                    jClass.fields.add(field);
                    tables.add(new ArrayTableConvention(
                            ExportableConvention.fieldExporter(field),
                            fieldGetter(GET_THIS, field)
                    ));
                    lTables.add(field);
                }
            }

            if (node.exports != null) {
                for (ExportNode export : node.exports) {
                    List<? extends ExportableConvention> ecl;
                    // @formatter:off
                    switch (export.type) {
                        case EXPORTS_FUNC: ecl = funcs; break;
                        case EXPORTS_TABLE: ecl = tables; break;
                        case EXPORTS_MEM: ecl = memories; break;
                        case EXPORTS_GLOBAL: ecl = globals; break;
                        default: throw new AssertionError();
                    }
                    // @formatter:on
                    ecl.get(export.index).export(export);
                }
            }
        }

        @Override
        public void buildConstructor() {
            ModuleNode node = module.getExtOrThrow(WasmExts.MODULE);
            Map<ExprNode, Function> funcMap = module.getExtOrThrow(WasmExts.FUNC_MAP);

            JavaExts.JavaMethod ctorMethod = new JavaExts.JavaMethod(
                    jClass,
                    "<init>",
                    "()V",
                    JavaExts.JavaMethod.Type.VIRTUAL
            );
            jClass.methods.add(ctorMethod);
            {
                Function ctorImpl = new Function();
                module.funcions.add(ctorImpl);
                ctorImpl.attachExt(JavaExts.FUNCTION_DESCRIPTOR, ctorMethod.descriptor);
                ctorImpl.attachExt(JavaExts.FUNCTION_OWNER, ctorMethod.owner);
                ctorMethod.attachExt(JavaExts.METHOD_IMPL, ctorImpl);

                IRBuilder ib = new IRBuilder(ctorImpl, ctorImpl.newBb());
                ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                        new JavaExts.JavaClass("java/lang/Object"),
                        "<init>",
                        "()V",
                        JavaExts.JavaMethod.Type.FINAL
                )).insn(IRUtils.getThis(ib)).assignTo());

                if (node.mems != null) {
                    int i = 0;
                    for (MemoryNode mem : node.mems) {
                        JavaExts.JavaField memField = lMemories.get(i++);
                        Var memV = ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                        IRUtils.BYTE_BUFFER_CLASS,
                                        "allocateDirect",
                                        "(I)Ljava/nio/ByteBuffer;",
                                        JavaExts.JavaMethod.Type.STATIC
                                )).insn(ib.insert(CommonOps.CONST.create(mem.limits.min * PAGE_SIZE).insn(),
                                        "size")),
                                "mem");
                        memV = ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                        IRUtils.BYTE_BUFFER_CLASS,
                                        "order",
                                        "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;",
                                        JavaExts.JavaMethod.Type.VIRTUAL
                                )).insn(memV,
                                        ib.insert(JavaOps.GET_FIELD.create(new JavaExts.JavaField(
                                                new JavaExts.JavaClass(Type.getInternalName(ByteOrder.class)),
                                                "LITTLE_ENDIAN",
                                                "Ljava/nio/ByteOrder;",
                                                true
                                        )).insn(), "order")),
                                "mem");
                        ib.insert(JavaOps.PUT_FIELD.create(memField)
                                .insn(IRUtils.getThis(ib), memV)
                                .assignTo());
                    }
                }

                if (node.tables != null) {
                    int i = 0;
                    for (TableNode table : node.tables) {
                        JavaExts.JavaField tableField = lTables.get(i++);
                        InsnList insns = new InsnList();
                        insns.add(new TypeInsnNode(Opcodes.ANEWARRAY, Type.getInternalName(MethodHandle.class)));
                        Var tableV = ib.insert(JavaOps.INSNS.create(insns)
                                        .insn(ib.insert(CommonOps.CONST.create(table.limits.min).insn(), "size")),
                                "table");
                        ib.insert(JavaOps.PUT_FIELD.create(tableField)
                                .insn(IRUtils.getThis(ib), tableV)
                                .assignTo());
                    }
                }

                if (node.globals != null) {
                    int i = 0;
                    for (GlobalNode global : node.globals) {
                        JavaExts.JavaField globalField = lGlobals.get(i++);
                        Var glInit = ib.insert(new Inliner(ib)
                                        .inline(funcMap.get(global.init), Collections.emptyList()),
                                "global_init");
                        ib.insert(JavaOps.PUT_FIELD.create(globalField)
                                .insn(IRUtils.getThis(ib), glInit)
                                .assignTo());
                    }
                }

                if (node.elems != null) {
                    for (ElementNode elem : node.elems) {
                        if (elem.offset == null) continue;

                        Var offset = ib.insert(new Inliner(ib)
                                        .inline(funcMap.get(elem.offset), Collections.emptyList()),
                                "elem_offset");

                        Var table = ib.insert(JavaOps.GET_FIELD
                                        .create(lTables.get(elem.table))
                                        .insn(IRUtils.getThis(ib)),
                                "table");

                        if (elem.indices != null) {
                            for (int i = 0; i < elem.indices.length; i++) {
                                Var j = ib.insert(JavaOps.insns(new org.objectweb.asm.tree.InsnNode(Opcodes.IADD))
                                                .insn(offset,
                                                        ib.insert(CommonOps.CONST.create(i)
                                                                .insn(), "i")),
                                        "j");
                                Var f = ib.func.newVar("f");
                                getFunction(elem.indices[i])
                                        .emitFuncRef(ib, WasmOps.FUNC_REF
                                                .create(elem.indices[i])
                                                .insn()
                                                .assignTo(f));
                                ib.insert(JavaOps.ARRAY_SET.create()
                                        .insn(table, j, f)
                                        .assignTo());
                            }
                        } else {
                            int i = 0;
                            for (ExprNode expr : elem.init) {
                                Var j = ib.insert(JavaOps.insns(new org.objectweb.asm.tree.InsnNode(Opcodes.IADD))
                                                .insn(offset,
                                                        ib.insert(CommonOps.CONST.create(i)
                                                                .insn(), "i")),
                                        "j");
                                Var f = ib.insert(new Inliner(ib).inline(funcMap.get(expr), Collections.emptyList()), "f");
                                ib.insert(JavaOps.ARRAY_SET.create()
                                        .insn(table, j, f)
                                        .assignTo());
                                i++;
                            }
                        }
                    }
                }

                if (node.datas != null) {
                    for (DataNode data : node.datas) {
                        JavaExts.JavaField mem = lMemories.get(data.memory);

                        Var dataOffset = ib.insert(new Inliner(ib)
                                        .inline(funcMap.get(data.offset), Collections.emptyList()),
                                "data_offset");

                        Var memV = ib.insert(JavaOps.GET_FIELD.create(mem)
                                        .insn(IRUtils.getThis(ib)),
                                "mem");
                        memV = ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                IRUtils.BYTE_BUFFER_CLASS,
                                "slice",
                                "()Ljava/nio/ByteBuffer;",
                                JavaExts.JavaMethod.Type.VIRTUAL
                        )).insn(memV), "sliced");
                        memV = ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                IRUtils.BYTE_BUFFER_CLASS,
                                "position",
                                "(I)Ljava/nio/ByteBuffer;",
                                JavaExts.JavaMethod.Type.VIRTUAL
                        )).insn(memV, dataOffset), "positioned");

                        // NB: Wasm memory is little endian, but when we write
                        // data segments we're calling #slice() first, which
                        // is always big endian
                        ByteBuffer buf = ByteBuffer.wrap(data.init);

                        JavaExts.JavaMethod putLong = new JavaExts.JavaMethod(
                                IRUtils.BYTE_BUFFER_CLASS,
                                "putLong",
                                "(J)Ljava/nio/ByteBuffer;",
                                JavaExts.JavaMethod.Type.VIRTUAL
                        );
                        JavaExts.JavaMethod putByte = new JavaExts.JavaMethod(
                                IRUtils.BYTE_BUFFER_CLASS,
                                "put",
                                "(B)Ljava/nio/ByteBuffer;",
                                JavaExts.JavaMethod.Type.VIRTUAL
                        );

                        while (buf.remaining() > Long.SIZE) {
                            memV = ib.insert(JavaOps.INVOKE.create(putLong)
                                            .insn(memV, ib.insert(CommonOps.CONST.create(buf.getLong()).insn(), "j")),
                                    "put");
                        }

                        while (buf.hasRemaining()) {
                            memV = ib.insert(JavaOps.INVOKE.create(putByte)
                                            .insn(memV, ib.insert(CommonOps.CONST.create((int) buf.get()).insn(), "b")),
                                    "put");
                        }
                    }
                }

                if (node.start != null) {
                    FunctionConvention startMethod = funcs.get(node.start);
                    assert node.types != null && node.types.types != null
                            && node.funcs != null && node.funcs.funcs != null;
                    startMethod.emitCall(ib, WasmOps.CALL
                            .create(new WasmOps.CallType(
                                    node.start,
                                    node.types.types.get(
                                            node.funcs.funcs.get(node.start).type
                                    )))
                            .insn()
                            .assignTo());
                }

                ib.insertCtrl(CommonOps.RETURN.insn().jumpsTo());
            }
        }

        @Override
        public FunctionConvention getFunction(int index) {
            return funcs.get(index);
        }

        @Override
        public GlobalConvention getGlobal(int index) {
            return globals.get(index);
        }

        @Override
        public MemoryConvention getMemory(int index) {
            return memories.get(index);
        }

        @Override
        public TableConvention getTable(int index) {
            return tables.get(index);
        }

        @Override
        public CallingConvention getIndirectCallingConvention() {
            return getCC();
        }
    }

}

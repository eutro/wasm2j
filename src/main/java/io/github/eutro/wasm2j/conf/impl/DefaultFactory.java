package io.github.eutro.wasm2j.conf.impl;

import io.github.eutro.jwasm.tree.*;
import io.github.eutro.wasm2j.conf.Conventions;
import io.github.eutro.wasm2j.conf.api.*;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ext.WasmExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.util.IRUtils;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.TypeInsnNode;

import java.lang.invoke.MethodHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static io.github.eutro.jwasm.Opcodes.*;
import static io.github.eutro.wasm2j.conf.Getters.GET_THIS;
import static io.github.eutro.wasm2j.conf.Getters.fieldGetter;

public class DefaultFactory implements WirJavaConventionFactory {
    final CallingConvention callingConvention;
    final ImportFactory<FuncImportNode, FunctionConvention> functionImports;
    final ImportFactory<GlobalImportNode, GlobalConvention> globalImports;
    final ImportFactory<MemImportNode, MemoryConvention> memoryImports;
    final ImportFactory<TableImportNode, TableConvention> tableImports;
    final UnaryOperator<FunctionConvention> modifyFuncConvention;
    final UnaryOperator<TableConvention> modifyTableConvention;
    final UnaryOperator<GlobalConvention> modifyGlobalConvention;
    final UnaryOperator<MemoryConvention> modifyMemConvention;
    final Supplier<String> nameSupplier;

    DefaultFactory(
            CallingConvention callingConvention,
            ImportFactory<FuncImportNode, FunctionConvention> functionImports,
            ImportFactory<GlobalImportNode, GlobalConvention> globalImports,
            ImportFactory<MemImportNode, MemoryConvention> memoryImports,
            ImportFactory<TableImportNode, TableConvention> tableImports,
            UnaryOperator<FunctionConvention> modifyFuncConvention,
            UnaryOperator<TableConvention> modifyTableConvention,
            UnaryOperator<GlobalConvention> modifyGlobalConvention,
            UnaryOperator<MemoryConvention> modifyMemConvention,
            Supplier<String> nameSupplier) {
        this.callingConvention = callingConvention;
        this.functionImports = functionImports;
        this.globalImports = globalImports;
        this.memoryImports = memoryImports;
        this.tableImports = tableImports;
        this.modifyFuncConvention = modifyFuncConvention;
        this.modifyTableConvention = modifyTableConvention;
        this.modifyGlobalConvention = modifyGlobalConvention;
        this.modifyMemConvention = modifyMemConvention;
        this.nameSupplier = nameSupplier;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ImportFactory<FuncImportNode, FunctionConvention> functionImports = unsupported("function imports");

        private ImportFactory<GlobalImportNode, GlobalConvention> globalImports = unsupported("global imports");

        private ImportFactory<MemImportNode, MemoryConvention> memoryImports = unsupported("memory imports");
        private ImportFactory<TableImportNode, TableConvention> tableImports = unsupported("table imports");
        private UnaryOperator<FunctionConvention> modifyFuncConvention = UnaryOperator.identity();
        private UnaryOperator<TableConvention> modifyTableConvention = UnaryOperator.identity();
        private UnaryOperator<GlobalConvention> modifyGlobalConvention = UnaryOperator.identity();
        private UnaryOperator<MemoryConvention> modifyMemConvention = UnaryOperator.identity();

        private CallingConvention callingConvention = Conventions.DEFAULT_CC;
        private Supplier<String> nameSupplier = () -> "com/example/FIXME";

        private static <Import extends AbstractImportNode, Convention>
        ImportFactory<Import, Convention> unsupported(String whats) {
            return (m, e, jClass) -> {
                throw new UnsupportedOperationException(whats + " are not supported");
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

        public Builder setModifyFuncConvention(UnaryOperator<FunctionConvention> modifyFuncConvention) {
            this.modifyFuncConvention = modifyFuncConvention;
            return this;
        }

        public Builder setModifyTableConvention(UnaryOperator<TableConvention> modifyTableConvention) {
            this.modifyTableConvention = modifyTableConvention;
            return this;
        }

        public Builder setModifyGlobalConvention(UnaryOperator<GlobalConvention> modifyGlobalConvention) {
            this.modifyGlobalConvention = modifyGlobalConvention;
            return this;
        }

        public Builder setModifyMemConvention(UnaryOperator<MemoryConvention> modifyMemConvention) {
            this.modifyMemConvention = modifyMemConvention;
            return this;
        }

        public Builder setNameSupplier(Supplier<String> nameSupplier) {
            this.nameSupplier = nameSupplier;
            return this;
        }

        public DefaultFactory build() {
            return new DefaultFactory(callingConvention,
                    functionImports,
                    globalImports,
                    memoryImports,
                    tableImports,
                    modifyFuncConvention,
                    modifyTableConvention,
                    modifyGlobalConvention,
                    modifyMemConvention,
                    nameSupplier);
        }
    }

    @Override
    public WirJavaConvention create(Module module) {
        JavaExts.JavaClass jClass = new JavaExts.JavaClass(nameSupplier.get());
        module.attachExt(JavaExts.JAVA_CLASS, jClass);
        return new DefaultWirJavaConvention(module, jClass);
    }

    public class DefaultWirJavaConvention implements WirJavaConvention {
        private final Module module;
        private final List<FunctionConvention> funcs = new ArrayList<>();
        private final List<TypeNode> funcTypes = new ArrayList<>();
        private final List<GlobalConvention> globals = new ArrayList<>();
        private final List<JavaExts.JavaField> lGlobals = new ArrayList<>();
        private final List<MemoryConvention> memories = new ArrayList<>();
        private final List<JavaExts.JavaField> lMemories = new ArrayList<>();
        private final List<TableConvention> tables = new ArrayList<>();
        private final List<JavaExts.JavaField> lTables = new ArrayList<>();
        private final JavaExts.JavaClass jClass;

        public DefaultWirJavaConvention(
                Module module,
                JavaExts.JavaClass jClass
        ) {
            this.module = module;
            this.jClass = jClass;
        }

        protected CallingConvention getCC() {
            return callingConvention;
        }

        protected FunctionConvention getImport(FuncImportNode funcImport) {
            return functionImports.createImport(module, funcImport, jClass);
        }

        protected MemoryConvention getImport(MemImportNode memImport) {
            return memoryImports.createImport(module, memImport, jClass);
        }

        protected TableConvention getImport(TableImportNode tableImport) {
            return tableImports.createImport(module, tableImport, jClass);
        }

        protected GlobalConvention getImport(GlobalImportNode globalImport) {
            return globalImports.createImport(module, globalImport, jClass);
        }

        @Override
        public void preEmit() {
            ModuleNode node = module.getExtOrThrow(WasmExts.MODULE);
            Map<ExprNode, Function> funcMap = module.getExtOrThrow(WasmExts.FUNC_MAP);

            if (node.imports != null && node.imports.imports != null) {
                for (AbstractImportNode importNode : node.imports) {
                    switch (importNode.importType()) {
                        case IMPORTS_FUNC:
                            FuncImportNode fin = (FuncImportNode) importNode;
                            funcs.add(getImport(fin));
                            funcTypes.add(Objects.requireNonNull(Objects.requireNonNull(node.types).types)
                                    .get(fin.type));
                            break;
                        // @formatter:off
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
                            JavaExts.JavaMethod.Kind.FINAL
                    );
                    jClass.methods.add(method);
                    funcs.add(modifyFuncConvention
                            .apply(new InstanceFunctionConvention(
                                    ExportableConvention.methodExporter(method),
                                    GET_THIS,
                                    method,
                                    getCC()
                            )));
                    funcTypes.add(typeNode);
                    Function implFunc = funcMap.get(it.next().expr);
                    method.attachExt(JavaExts.METHOD_IMPL, implFunc);
                    implFunc.attachExt(JavaExts.FUNCTION_METHOD, method);
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
                    globals.add(modifyGlobalConvention
                            .apply(new FieldGlobalConvention(
                                    ExportableConvention.fieldExporter(field),
                                    GET_THIS,
                                    field
                            )));
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
                    memories.add(modifyMemConvention
                            .apply(new ByteBufferMemoryConvention(
                                    ExportableConvention.fieldExporter(field),
                                    fieldGetter(GET_THIS, field)
                            )));
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
                    tables.add(modifyTableConvention
                            .apply(new ArrayTableConvention(
                                    ExportableConvention.fieldExporter(field),
                                    fieldGetter(GET_THIS, field)
                            )));
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
                    ecl.get(export.index).export(export, module, jClass);
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
                    JavaExts.JavaMethod.Kind.VIRTUAL
            );
            jClass.methods.add(ctorMethod);
            {
                Function ctorImpl = new Function();
                module.functions.add(ctorImpl);
                ctorImpl.attachExt(JavaExts.FUNCTION_METHOD, ctorMethod);
                ctorImpl.attachExt(JavaExts.FUNCTION_OWNER, ctorMethod.owner);
                ctorMethod.attachExt(JavaExts.METHOD_IMPL, ctorImpl);

                IRBuilder ib = new IRBuilder(ctorImpl, ctorImpl.newBb());
                ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                        new JavaExts.JavaClass("java/lang/Object"),
                        "<init>",
                        "()V",
                        JavaExts.JavaMethod.Kind.FINAL
                )).insn(IRUtils.getThis(ib)).assignTo());

                if (node.mems != null) {
                    int i = 0;
                    for (MemoryNode mem : node.mems) {
                        JavaExts.JavaField memField = lMemories.get(i++);
                        Var memV = ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                        IRUtils.BYTE_BUFFER_CLASS,
                                        "allocateDirect",
                                        "(I)Ljava/nio/ByteBuffer;",
                                        JavaExts.JavaMethod.Kind.STATIC
                                )).insn(ib.insert(CommonOps.CONST.create(mem.limits.min * PAGE_SIZE).insn(),
                                        "size")),
                                "mem");
                        memV = ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                        IRUtils.BYTE_BUFFER_CLASS,
                                        "order",
                                        "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;",
                                        JavaExts.JavaMethod.Kind.VIRTUAL
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
                        insns.add(new TypeInsnNode(Opcodes.ANEWARRAY,
                                BasicCallingConvention.javaType(table.type)
                                        .getInternalName()));
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
                        Function globalFunc = funcMap.get(global.init);
                        Var glInit = ib.insert(new Inliner(ib)
                                        .inline(globalFunc, Collections.emptyList()),
                                "global_init");
                        module.functions.remove(globalFunc);
                        ib.insert(JavaOps.PUT_FIELD.create(globalField)
                                .insn(IRUtils.getThis(ib), glInit)
                                .assignTo());
                    }
                }

                if (node.elems != null) {
                    int fieldElems = 0;
                    for (ElementNode elem : node.elems) {
                        Var offset, table;
                        if (elem.offset == null) {
                            offset = ib.insert(CommonOps.CONST.create(0).insn(), "offset");
                            table = ib.insert(JavaOps.insns(
                                            new TypeInsnNode(Opcodes.ANEWARRAY, Type.getInternalName(MethodHandle.class))
                                    ).insn(ib.insert(CommonOps.CONST.create(elem.indices != null ? elem.indices.length : elem.init.size()).insn(),
                                            "elemsz")),
                                    "elem");
                            JavaExts.JavaField field = new JavaExts.JavaField(jClass, "elem" + fieldElems++,
                                    Type.getDescriptor(MethodHandle[].class),
                                    false);
                            jClass.fields.add(field);
                            ib.insert(JavaOps.PUT_FIELD.create(field).insn(IRUtils.getThis(ib), table).assignTo());
                        } else {
                            Function offsetFunc = funcMap.get(elem.offset);
                            offset = ib.insert(new Inliner(ib)
                                            .inline(offsetFunc, Collections.emptyList()),
                                    "elem_offset");
                            module.functions.remove(offsetFunc);
                            table = ib.insert(JavaOps.GET_FIELD
                                            .create(lTables.get(elem.table))
                                            .insn(IRUtils.getThis(ib)),
                                    "table");
                        }

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
                                Function exprFunc = funcMap.get(expr);
                                Var f = ib.insert(new Inliner(ib).inline(exprFunc, Collections.emptyList()), "f");
                                module.functions.remove(exprFunc);
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
                        if (data.offset == null) continue;

                        JavaExts.JavaField mem = lMemories.get(data.memory);

                        Function offsetFunc = funcMap.get(data.offset);
                        Var dataOffset = ib.insert(new Inliner(ib)
                                        .inline(offsetFunc, Collections.emptyList()),
                                "data_offset");
                        module.functions.remove(offsetFunc);

                        Var memV = ib.insert(JavaOps.GET_FIELD.create(mem)
                                        .insn(IRUtils.getThis(ib)),
                                "mem");
                        memV = ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                IRUtils.BYTE_BUFFER_CLASS,
                                "slice",
                                "()Ljava/nio/ByteBuffer;",
                                JavaExts.JavaMethod.Kind.VIRTUAL
                        )).insn(memV), "sliced");
                        memV = ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                IRUtils.BYTE_BUFFER_CLASS,
                                "position",
                                "(I)Ljava/nio/ByteBuffer;",
                                JavaExts.JavaMethod.Kind.VIRTUAL
                        )).insn(memV, dataOffset), "positioned");

                        // NB: Wasm memory is little endian, but when we write
                        // data segments we're calling #slice() first, which
                        // is always big endian
                        ByteBuffer buf = ByteBuffer.wrap(data.init);

                        JavaExts.JavaMethod putLong = new JavaExts.JavaMethod(
                                IRUtils.BYTE_BUFFER_CLASS,
                                "putLong",
                                "(J)Ljava/nio/ByteBuffer;",
                                JavaExts.JavaMethod.Kind.VIRTUAL
                        );
                        JavaExts.JavaMethod putByte = new JavaExts.JavaMethod(
                                IRUtils.BYTE_BUFFER_CLASS,
                                "put",
                                "(B)Ljava/nio/ByteBuffer;",
                                JavaExts.JavaMethod.Kind.VIRTUAL
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

                for (ImportFactory<?, ?> factory : Arrays.asList(
                        functionImports,
                        globalImports,
                        memoryImports,
                        tableImports
                )) {
                    factory.modifyConstructor(ib, ctorMethod, jClass);
                }

                if (node.start != null) {
                    FunctionConvention startMethod = funcs.get(node.start);
                    assert node.types != null && node.types.types != null
                            && node.funcs != null && node.funcs.funcs != null;
                    startMethod.emitCall(ib, WasmOps.CALL
                            .create(new WasmOps.CallType(
                                    node.start,
                                    funcTypes.get(node.start)
                            ))
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

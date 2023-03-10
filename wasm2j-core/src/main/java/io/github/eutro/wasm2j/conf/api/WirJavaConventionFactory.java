package io.github.eutro.wasm2j.conf.api;

import io.github.eutro.jwasm.tree.*;
import io.github.eutro.wasm2j.conf.Conventions;
import io.github.eutro.wasm2j.conf.impl.*;
import io.github.eutro.wasm2j.ext.JavaExts;
import io.github.eutro.wasm2j.ext.WasmExts;
import io.github.eutro.wasm2j.ops.CommonOps;
import io.github.eutro.wasm2j.ops.JavaOps;
import io.github.eutro.wasm2j.ops.Op;
import io.github.eutro.wasm2j.ops.WasmOps;
import io.github.eutro.wasm2j.passes.IRPass;
import io.github.eutro.wasm2j.ssa.*;
import io.github.eutro.wasm2j.ssa.Module;
import io.github.eutro.wasm2j.util.IRUtils;
import io.github.eutro.wasm2j.util.Lazy;
import io.github.eutro.wasm2j.util.Pair;
import io.github.eutro.wasm2j.util.ValueGetterSetter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.TypeInsnNode;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static io.github.eutro.jwasm.Opcodes.*;
import static io.github.eutro.wasm2j.conf.Getters.GET_THIS;
import static io.github.eutro.wasm2j.conf.Getters.fieldGetter;
import static io.github.eutro.wasm2j.util.Lazy.lazy;

public interface WirJavaConventionFactory {
    WirJavaConvention create(Module module, JClass jClass);

    class Builder {
        private ImportFactory<FuncImportNode, FunctionConvention> functionImports = unsupported("function imports");

        private ImportFactory<GlobalImportNode, GlobalConvention> globalImports = unsupported("global imports");

        private ImportFactory<MemImportNode, MemoryConvention> memoryImports = unsupported("memory imports");
        private ImportFactory<TableImportNode, TableConvention> tableImports = unsupported("table imports");
        private ConventionModifier<FunctionConvention, Pair<FuncNode, CodeNode>> modifyFuncConvention = ConventionModifier.identity();
        private ConventionModifier<TableConvention, TableNode> modifyTableConvention = ConventionModifier.identity();
        private ConventionModifier<GlobalConvention, GlobalNode> modifyGlobalConvention = ConventionModifier.identity();
        private ConventionModifier<MemoryConvention, MemoryNode> modifyMemConvention = ConventionModifier.identity();
        private final List<ConstructorCallback> constructorCallbacks = new ArrayList<>();

        private CallingConvention callingConvention = Conventions.DEFAULT_CC;
        private Supplier<String> nameSupplier = () -> "com/example/FIXME";

        private static <Import extends AbstractImportNode, Convention>
        ImportFactory<Import, Convention> unsupported(String whats) {
            return (m, e, jClass, idx) -> {
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

        public Builder setModifyFuncConvention(ConventionModifier<FunctionConvention, Pair<FuncNode, CodeNode>> modifyFuncConvention) {
            this.modifyFuncConvention = this.modifyFuncConvention.andThen(modifyFuncConvention);
            return this;
        }

        public Builder setModifyTableConvention(ConventionModifier<TableConvention, TableNode> modifyTableConvention) {
            this.modifyTableConvention = this.modifyTableConvention.andThen(modifyTableConvention);
            return this;
        }

        public Builder setModifyGlobalConvention(ConventionModifier<GlobalConvention, GlobalNode> modifyGlobalConvention) {
            this.modifyGlobalConvention = this.modifyGlobalConvention.andThen(modifyGlobalConvention);
            return this;
        }

        public Builder setModifyMemConvention(ConventionModifier<MemoryConvention, MemoryNode> modifyMemConvention) {
            this.modifyMemConvention = this.modifyMemConvention.andThen(modifyMemConvention);
            return this;
        }

        public Builder setNameSupplier(Supplier<String> nameSupplier) {
            this.nameSupplier = nameSupplier;
            return this;
        }

        public Builder addConstructorCallback(ConstructorCallback cb) {
            constructorCallbacks.add(cb);
            return this;
        }

        public WirJavaConventionFactory build() {
            return new Impl();
        }

        class Impl implements WirJavaConventionFactory {
            @Override
            public WirJavaConvention create(Module module, JClass jClass) {
                jClass.name = nameSupplier.get();
                return new DefaultWirJavaConvention(module, jClass);
            }

            class DefaultWirJavaConvention implements WirJavaConvention {
                private final Module module;
                private final List<FunctionConvention> funcs = new ArrayList<>();
                private final List<TypeNode> funcTypes = new ArrayList<>();
                private final List<GlobalConvention> globals = new ArrayList<>();
                private int iGlobals = 0;
                private final List<JClass.JavaField> lGlobals = new ArrayList<>();
                private final List<MemoryConvention> memories = new ArrayList<>();
                private int iMemories = 0;
                private final List<JClass.JavaField> lMemories = new ArrayList<>();
                private final List<TableConvention> tables = new ArrayList<>();
                private int iTables = 0;
                private final List<JClass.JavaField> lTables = new ArrayList<>();
                private final JClass jClass;
                private final List<JClass.JavaField> datas = new ArrayList<>();
                private final List<JClass.JavaMethod> dataInits = new ArrayList<>();
                private final List<JClass.JavaField> elems = new ArrayList<>();

                public DefaultWirJavaConvention(
                        Module module,
                        JClass jClass
                ) {
                    this.module = module;
                    this.jClass = jClass;
                }

                private CallingConvention getCC() {
                    return callingConvention;
                }

                private FunctionConvention getImport(FuncImportNode funcImport) {
                    return functionImports.createImport(module, funcImport, jClass, funcs.size());
                }

                private MemoryConvention getImport(MemImportNode memImport) {
                    return memoryImports.createImport(module, memImport, jClass, memories.size());
                }

                private TableConvention getImport(TableImportNode tableImport) {
                    return tableImports.createImport(module, tableImport, jClass, tables.size());
                }

                private GlobalConvention getImport(GlobalImportNode globalImport) {
                    return globalImports.createImport(module, globalImport, jClass, globals.size());
                }

                @Override
                public void convert(IRPass<Function, Function> convert) {
                    ModuleNode node = module.getExtOrThrow(WasmExts.MODULE);
                    Map<ExprNode, Lazy<Function>> funcMap = module.funcMap;

                    if (node.imports != null) {
                        for (AbstractImportNode importNode : node.imports) {
                            switch (importNode.importType()) {
                                case IMPORTS_FUNC:
                                    FuncImportNode fin = (FuncImportNode) importNode;
                                    funcs.add(getImport(fin));
                                    funcTypes.add(Objects.requireNonNull(Objects.requireNonNull(node.types).types)
                                            .get(fin.type));
                                    break;
                                case IMPORTS_GLOBAL:
                                    globals.add(getImport((GlobalImportNode) importNode));
                                    iGlobals++;
                                    lGlobals.add(null);
                                    break;
                                case IMPORTS_MEM:
                                    memories.add(getImport((MemImportNode) importNode));
                                    iMemories++;
                                    lMemories.add(null);
                                    break;
                                case IMPORTS_TABLE:
                                    tables.add(getImport((TableImportNode) importNode));
                                    iTables++;
                                    lTables.add(null);
                                    break;
                                default:
                                    throw new AssertionError();
                            }
                        }
                    }

                    if (node.datas != null) {
                        int dataIdx = -1;
                        for (DataNode data : node.datas) {
                            dataIdx++;
                            JClass.JavaField field = new JClass.JavaField(
                                    jClass,
                                    "data" + dataIdx,
                                    Type.getDescriptor(ByteBuffer.class),
                                    false
                            );
                            JClass.JavaMethod method = new JClass.JavaMethod(
                                    jClass,
                                    "initData" + dataIdx,
                                    "()V",
                                    JClass.JavaMethod.Kind.FINAL
                            );
                            jClass.fields.add(field);
                            jClass.methods.add(method);
                            datas.add(field);
                            dataInits.add(method);

                            method.attachExt(JavaExts.METHOD_IMPL, lazy(() -> {
                                Function func = new Function();
                                func.attachExt(JavaExts.FUNCTION_OWNER, jClass);
                                func.attachExt(JavaExts.FUNCTION_METHOD, method);

                                IRBuilder dIb = new IRBuilder(func, func.newBb());
                                Var dataV = dIb.insert(JavaOps.INVOKE.create(new JClass.JavaMethod(
                                                        IRUtils.BYTE_BUFFER_CLASS,
                                                        "allocate",
                                                        "(I)Ljava/nio/ByteBuffer;",
                                                        JClass.JavaMethod.Kind.STATIC
                                                ))
                                                .insn(dIb.insert(CommonOps.constant(data.init.length), "len")),
                                        "data");

                                Var slicedV = dIb.insert(JavaOps.INVOKE.create(new JClass.JavaMethod(
                                                IRUtils.BYTE_BUFFER_CLASS,
                                                "slice",
                                                "()Ljava/nio/ByteBuffer;",
                                                JClass.JavaMethod.Kind.VIRTUAL
                                        )).insn(dataV),
                                        "sliced");

                                IRUtils.fillAuto(data, dIb, slicedV);

                                dIb.insert(JavaOps.PUT_FIELD.create(field)
                                        .insn(IRUtils.getThis(dIb), dataV)
                                        .assignTo());
                                dIb.insertCtrl(CommonOps.RETURN.insn().jumpsTo());

                                return func;
                            }));
                        }
                    }

                    if (node.elems != null) {
                        int elemIdx = -1;
                        for (ElementNode elem : node.elems) {
                            elemIdx++;
                            Type elemType = BasicCallingConvention.javaType(elem.type);
                            JClass.JavaField field = new JClass.JavaField(jClass, "elem" + elemIdx,
                                    "[" + elemType.getDescriptor(),
                                    false);
                            jClass.fields.add(field);
                            elems.add(field);
                        }
                    }

                    if (node.funcs != null && !node.funcs.funcs.isEmpty()) {
                        assert node.types != null;
                        assert node.codes != null;
                        int i = 0;
                        Iterator<CodeNode> it = node.codes.codes.iterator();
                        for (FuncNode fn : node.funcs) {
                            CodeNode code = it.next();
                            TypeNode typeNode = node.types.types.get(fn.type);
                            JClass.JavaMethod method = new JClass.JavaMethod(
                                    jClass,
                                    "func" + i++,
                                    getCC().getDescriptor(typeNode).getDescriptor(),
                                    JClass.JavaMethod.Kind.FINAL
                            );
                            jClass.methods.add(method);
                            funcs.add(modifyFuncConvention
                                    .modify(new InstanceFunctionConvention(
                                                    ExportableConvention.methodExporter(method),
                                                    GET_THIS,
                                                    method,
                                                    getCC()
                                            ),
                                            Pair.of(fn, code),
                                            funcs.size()));
                            funcTypes.add(typeNode);
                            Lazy<Function> impl = funcMap.remove(code.expr);
                            impl.mapInPlace(implFunc -> {
                                implFunc.attachExt(JavaExts.FUNCTION_METHOD, method);
                                implFunc.attachExt(JavaExts.FUNCTION_OWNER, jClass);
                                return convert.run(implFunc);
                            });
                            method.attachExt(JavaExts.METHOD_IMPL, impl);
                        }
                    }

                    if (node.globals != null) {
                        int i = 0;
                        for (GlobalNode global : node.globals) {
                            JClass.JavaField field = new JClass.JavaField(
                                    jClass,
                                    "global" + i++,
                                    BasicCallingConvention.javaType(global.type.type).getDescriptor(),
                                    false
                            );
                            jClass.fields.add(field);
                            globals.add(modifyGlobalConvention
                                    .modify(new FieldGlobalConvention(
                                                    ExportableConvention.fieldExporter(field),
                                                    GET_THIS,
                                                    field
                                            ),
                                            global,
                                            globals.size()));
                            lGlobals.add(field);
                        }
                    }

                    if (node.mems != null) {
                        int i = 0;
                        for (MemoryNode memory : node.mems) {
                            JClass.JavaField field = new JClass.JavaField(
                                    jClass,
                                    "mem" + i++,
                                    Type.getDescriptor(ByteBuffer.class),
                                    false
                            );
                            jClass.fields.add(field);
                            memories.add(modifyMemConvention
                                    .modify(new ByteBufferMemoryConvention(
                                                    ExportableConvention.fieldExporter(field),
                                                    fieldGetter(GET_THIS, field),
                                                    memory.limits.max
                                            ),
                                            memory,
                                            memories.size()));
                            lMemories.add(field);
                        }
                    }

                    if (node.tables != null) {
                        int i = 0;
                        for (TableNode table : node.tables) {
                            Type componentType = BasicCallingConvention.javaType(table.type);
                            JClass.JavaField field = new JClass.JavaField(
                                    jClass,
                                    "table" + i++,
                                    "[" + componentType.getDescriptor(),
                                    false
                            );
                            jClass.fields.add(field);
                            tables.add(modifyTableConvention
                                    .modify(new ArrayTableConvention(
                                                    ExportableConvention.fieldExporter(field),
                                                    fieldGetter(GET_THIS, field),
                                                    componentType,
                                                    table.limits.max
                                            ),
                                            table,
                                            tables.size()));
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

                    JClass.JavaMethod ctorMethod = new JClass.JavaMethod(
                            jClass,
                            "<init>",
                            "()V",
                            JClass.JavaMethod.Kind.VIRTUAL
                    );
                    jClass.methods.add(ctorMethod);
                    Function ctorImpl = new Function();
                    ctorImpl.attachExt(JavaExts.FUNCTION_METHOD, ctorMethod);
                    ctorImpl.attachExt(JavaExts.FUNCTION_OWNER, ctorMethod.owner);

                    IRBuilder ib = new IRBuilder(ctorImpl, ctorImpl.newBb());
                    ib.insert(JavaOps.INVOKE.create(new JClass.JavaMethod(
                            new JClass("java/lang/Object"),
                            "<init>",
                            "()V",
                            JClass.JavaMethod.Kind.FINAL
                    )).insn(IRUtils.getThis(ib)).assignTo());

                    Stream.of(funcs, globals, tables, memories, constructorCallbacks)
                            .flatMap(Collection::stream)
                            .forEach(it -> it.modifyConstructor(ib, ctorMethod, module, jClass));

                    // fetching values used in ctor
                    ArrayList<Lazy<Function>>
                            globalInits = new ArrayList<>(),
                            elemOffsets = new ArrayList<>(),
                            dataOffsets = new ArrayList<>();
                    ArrayList<List<Lazy<Function>>> elemInits = new ArrayList<>();
                    {
                        if (node.globals != null) {
                            for (GlobalNode global : node.globals) {
                                globalInits.add(funcMap.remove(global.init));
                            }
                        }
                        if (node.elems != null) {
                            for (ElementNode elem : node.elems) {
                                if (elem.indices == null) {
                                    ArrayList<Lazy<Function>> inits = new ArrayList<>();
                                    elemInits.add(inits);
                                    for (ExprNode expr : elem.init) {
                                        inits.add(funcMap.remove(expr));
                                    }
                                    inits.trimToSize();
                                }
                                if (elem.offset != null) {
                                    elemOffsets.add(funcMap.remove(elem.offset));
                                }
                            }
                        }
                        if (node.datas != null) {
                            for (DataNode data : node.datas) {
                                if (data.offset != null) {
                                    dataOffsets.add(funcMap.remove(data.offset));
                                }
                            }
                        }
                        globalInits.trimToSize();
                        elemOffsets.trimToSize();
                        dataOffsets.trimToSize();
                        elemInits.trimToSize();
                    }

                    ctorMethod.attachExt(JavaExts.METHOD_IMPL, lazy(() -> {
                        if (node.mems != null) {
                            int i = iMemories;
                            for (MemoryNode mem : node.mems) {
                                JClass.JavaField memField = lMemories.get(i++);
                                Var memV = ib.insert(JavaOps.INVOKE.create(new JClass.JavaMethod(
                                                IRUtils.BYTE_BUFFER_CLASS,
                                                "allocateDirect",
                                                "(I)Ljava/nio/ByteBuffer;",
                                                JClass.JavaMethod.Kind.STATIC
                                        )).insn(ib.insert(CommonOps.constant(mem.limits.min * PAGE_SIZE),
                                                "size")),
                                        "mem");
                                memV = ib.insert(JavaOps.INVOKE.create(new JClass.JavaMethod(
                                                IRUtils.BYTE_BUFFER_CLASS,
                                                "order",
                                                "(Ljava/nio/ByteOrder;)Ljava/nio/ByteBuffer;",
                                                JClass.JavaMethod.Kind.VIRTUAL
                                        )).insn(memV,
                                                ib.insert(JavaOps.GET_FIELD.create(new JClass.JavaField(
                                                        new JClass(Type.getInternalName(ByteOrder.class)),
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
                            int i = iTables;
                            for (TableNode table : node.tables) {
                                JClass.JavaField tableField = lTables.get(i++);
                                InsnList insns = new InsnList();
                                insns.add(new TypeInsnNode(Opcodes.ANEWARRAY,
                                        BasicCallingConvention.javaType(table.type)
                                                .getInternalName()));
                                Var tableV = ib.insert(JavaOps.INSNS.create(insns)
                                                .insn(ib.insert(CommonOps.constant(table.limits.min), "size")),
                                        "table");
                                ib.insert(JavaOps.PUT_FIELD.create(tableField)
                                        .insn(IRUtils.getThis(ib), tableV)
                                        .assignTo());
                            }
                        }

                        if (node.globals != null) {
                            int i = iGlobals;
                            for (Lazy<Function> init : globalInits) {
                                JClass.JavaField globalField = lGlobals.get(i++);
                                Var glInit = ib.insert(new Inliner(ib)
                                                .inline(convert.run(init.get()),
                                                        Collections.emptyList()),
                                        "global_init");
                                ib.insert(JavaOps.PUT_FIELD.create(globalField)
                                        .insn(IRUtils.getThis(ib), glInit)
                                        .assignTo());
                            }
                        }

                        if (node.elems != null) {
                            int elemIdx = 0;
                            Iterator<Lazy<Function>> offsets = elemOffsets.iterator();
                            Iterator<List<Lazy<Function>>> inits = elemInits.iterator();
                            for (ElementNode elem : node.elems) {
                                Insn elemLen = CommonOps.constant(elem.indices != null ? elem.indices.length : elem.init.size());
                                Type elemType = BasicCallingConvention.javaType(elem.type);
                                Op aNewArray = JavaOps.insns(new TypeInsnNode(Opcodes.ANEWARRAY, elemType.getInternalName()));
                                Var elemV = ib.insert(aNewArray.insn(ib.insert(elemLen, "elemsz")),
                                        "elem");
                                Var toAssign;
                                if (elem.offset == null && elem.passive) {
                                    toAssign = elemV;
                                } else {
                                    toAssign = ib.insert(aNewArray.insn(ib.insert(CommonOps.constant(0), "0")), "empty");
                                }
                                ib.insert(JavaOps.PUT_FIELD.create(elems.get(elemIdx))
                                        .insn(IRUtils.getThis(ib), toAssign).assignTo());

                                if (elem.indices != null) {
                                    for (int i = 0; i < elem.indices.length; i++) {
                                        Var j = ib.insert(CommonOps.constant(i), "j");
                                        Var f = ib.func.newVar("f");
                                        getFunction(elem.indices[i])
                                                .emitFuncRef(ib, WasmOps.FUNC_REF
                                                        .create(elem.indices[i])
                                                        .insn()
                                                        .assignTo(f));
                                        ib.insert(JavaOps.ARRAY_SET.create()
                                                .insn(elemV, j, f)
                                                .assignTo());
                                    }
                                } else {
                                    int i = 0;
                                    for (Lazy<Function> expr : inits.next()) {
                                        Var j = ib.insert(CommonOps.constant(i), "j");
                                        Function exprFunc = expr.get();
                                        Var f = ib.insert(new Inliner(ib)
                                                        .inline(convert.run(exprFunc),
                                                                Collections.emptyList()),
                                                "f");
                                        ib.insert(JavaOps.ARRAY_SET.create()
                                                .insn(elemV, j, f)
                                                .assignTo());
                                        i++;
                                    }
                                }

                                if (elem.offset != null) {
                                    Var offset = ib.insert(new Inliner(ib)
                                                    .inline(convert.run(offsets.next().get()),
                                                            Collections.emptyList()),
                                            "elem_offset");
                                    getTable(elem.table)
                                            .emitTableInit(ib,
                                                    WasmOps.TABLE_INIT
                                                            .create(Pair.of(elem.table, elemIdx))
                                                            .insn(offset,
                                                                    ib.insert(CommonOps.constant(0), "src"),
                                                                    ib.insert(elemLen, "len"))
                                                            .assignTo(),
                                                    elemV);
                                }

                                elemIdx++;
                            }
                        }

                        if (node.datas != null) {
                            int dataIdx = -1;
                            Iterator<Lazy<Function>> offsets = dataOffsets.iterator();
                            for (DataNode data : node.datas) {
                                dataIdx++;

                                int fDataIdx = dataIdx;
                                JClass.JavaMethod method = dataInits.get(dataIdx);

                                ib.insert(JavaOps.INVOKE.create(method).insn(IRUtils.getThis(ib)).assignTo());

                                if (data.offset != null) {
                                    MemoryConvention mem = memories.get(data.memory);


                                    Var dataOffset = ib.insert(new Inliner(ib)
                                                    .inline(convert.run(offsets.next().get()), Collections.emptyList()),
                                            "dataOffset");

                                    mem.emitMemInit(ib, WasmOps.MEM_INIT
                                                    .create(Pair.of(data.memory, fDataIdx))
                                                    .insn(dataOffset,
                                                            ib.insert(CommonOps.constant(0), "offset"),
                                                            ib.insert(CommonOps.constant(data.init.length), "len"))
                                                    .assignTo(),
                                            ib.insert(JavaOps.GET_FIELD
                                                    .create(datas.get(fDataIdx)).insn(IRUtils.getThis(ib)), "data"));
                                }
                            }
                        }

                        if (node.start != null) {
                            FunctionConvention startMethod = funcs.get(node.start);
                            startMethod.emitCall(ib, WasmOps.CALL
                                    .create(new WasmOps.CallType(
                                            node.start,
                                            funcTypes.get(node.start)
                                    ))
                                    .insn()
                                    .assignTo());
                        }

                        ib.insertCtrl(CommonOps.RETURN.insn().jumpsTo());

                        return ib.func;
                    }));
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
                public DataConvention getData(int index) {
                    return () -> fieldGetter(GET_THIS, datas.get(index));
                }

                @Override
                public ElemConvention getElem(int index) {
                    JClass.JavaField elem = elems.get(index);
                    return new ElemConvention() {
                        @Override
                        public Type elementType() {
                            return Type.getType(elem.descriptor).getElementType();
                        }

                        @Override
                        public ValueGetterSetter array() {
                            return fieldGetter(GET_THIS, elem);
                        }
                    };
                }

                @Override
                public CallingConvention getIndirectCallingConvention() {
                    return getCC();
                }
            }
        }
    }

    static Builder builder() {
        return new Builder();
    }
}

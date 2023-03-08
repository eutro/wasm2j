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

public interface WirJavaConventionFactory {
    WirJavaConvention create(Module module, Module extrasModule, IRPass<Function, Function> convertWir);

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
            this.modifyFuncConvention = modifyFuncConvention;
            return this;
        }

        public Builder setModifyTableConvention(ConventionModifier<TableConvention, TableNode> modifyTableConvention) {
            this.modifyTableConvention = modifyTableConvention;
            return this;
        }

        public Builder setModifyGlobalConvention(ConventionModifier<GlobalConvention, GlobalNode> modifyGlobalConvention) {
            this.modifyGlobalConvention = modifyGlobalConvention;
            return this;
        }

        public Builder setModifyMemConvention(ConventionModifier<MemoryConvention, MemoryNode> modifyMemConvention) {
            this.modifyMemConvention = modifyMemConvention;
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
            public WirJavaConvention create(Module module, Module extrasModule, IRPass<Function, Function> convert) {
                JavaExts.JavaClass jClass = new JavaExts.JavaClass(nameSupplier.get());
                module.attachExt(JavaExts.JAVA_CLASS, jClass);
                return new DefaultWirJavaConvention(module, extrasModule, jClass, convert);
            }

            class DefaultWirJavaConvention implements WirJavaConvention {
                private final Module module;
                private final Module extrasModule;
                private final IRPass<Function, Function> convert;
                private final List<FunctionConvention> funcs = new ArrayList<>();
                private final List<TypeNode> funcTypes = new ArrayList<>();
                private final List<GlobalConvention> globals = new ArrayList<>();
                private int iGlobals = 0;
                private final List<JavaExts.JavaField> lGlobals = new ArrayList<>();
                private final List<MemoryConvention> memories = new ArrayList<>();
                private int iMemories = 0;
                private final List<JavaExts.JavaField> lMemories = new ArrayList<>();
                private final List<TableConvention> tables = new ArrayList<>();
                private int iTables = 0;
                private final List<JavaExts.JavaField> lTables = new ArrayList<>();
                private final JavaExts.JavaClass jClass;
                private final List<JavaExts.JavaField> datas = new ArrayList<>();
                private final List<JavaExts.JavaField> elems = new ArrayList<>();

                public DefaultWirJavaConvention(
                        Module module,
                        Module extrasModule,
                        JavaExts.JavaClass jClass,
                        IRPass<Function, Function> convert) {
                    this.module = module;
                    this.jClass = jClass;
                    this.extrasModule = extrasModule;
                    this.convert = convert;
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
                public void preConvert() {
                    ModuleNode node = module.getExtOrThrow(WasmExts.MODULE);
                    Map<ExprNode, Function> funcMap = module.getExtOrThrow(WasmExts.FUNC_MAP);

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

                    if (node.funcs != null && !node.funcs.funcs.isEmpty()) {
                        assert node.types != null;
                        assert node.codes != null;
                        int i = 0;
                        Iterator<CodeNode> it = node.codes.codes.iterator();
                        for (FuncNode fn : node.funcs) {
                            CodeNode code = it.next();
                            TypeNode typeNode = node.types.types.get(fn.type);
                            JavaExts.JavaMethod method = new JavaExts.JavaMethod(
                                    jClass,
                                    "func" + i++,
                                    getCC().getDescriptor(typeNode).getDescriptor(),
                                    JavaExts.JavaMethod.Kind.FINAL
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
                            Function implFunc = funcMap.get(code.expr);
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
                            JavaExts.JavaField field = new JavaExts.JavaField(
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
                            JavaExts.JavaField field = new JavaExts.JavaField(
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
                            ecl.get(export.index).export(export, extrasModule, jClass);
                        }
                    }

                    JavaExts.JavaMethod ctorMethod = new JavaExts.JavaMethod(
                            jClass,
                            "<init>",
                            "()V",
                            JavaExts.JavaMethod.Kind.VIRTUAL
                    );
                    jClass.methods.add(ctorMethod);
                    Function ctorImpl = new Function();
                    extrasModule.functions.add(ctorImpl);
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

                    Stream.of(funcs, globals, tables, memories, constructorCallbacks)
                            .flatMap(Collection::stream)
                            .forEach(it -> it.modifyConstructor(ib, ctorMethod, extrasModule, jClass));

                    if (node.mems != null) {
                        int i = iMemories;
                        for (MemoryNode mem : node.mems) {
                            JavaExts.JavaField memField = lMemories.get(i++);
                            Var memV = ib.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                            IRUtils.BYTE_BUFFER_CLASS,
                                            "allocateDirect",
                                            "(I)Ljava/nio/ByteBuffer;",
                                            JavaExts.JavaMethod.Kind.STATIC
                                    )).insn(ib.insert(CommonOps.constant(mem.limits.min * PAGE_SIZE),
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
                        int i = iTables;
                        for (TableNode table : node.tables) {
                            JavaExts.JavaField tableField = lTables.get(i++);
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
                        for (GlobalNode global : node.globals) {
                            JavaExts.JavaField globalField = lGlobals.get(i++);
                            Function globalFunc = funcMap.get(global.init);
                            Var glInit = ib.insert(new Inliner(ib)
                                            .inline(convert.run(globalFunc), Collections.emptyList()),
                                    "global_init");
                            module.functions.remove(globalFunc);
                            ib.insert(JavaOps.PUT_FIELD.create(globalField)
                                    .insn(IRUtils.getThis(ib), glInit)
                                    .assignTo());
                        }
                    }

                    if (node.elems != null) {
                        int elemIdx = 0;
                        for (ElementNode elem : node.elems) {
                            Insn elemLen = CommonOps.constant(elem.indices != null ? elem.indices.length : elem.init.size());
                            Type elemType = BasicCallingConvention.javaType(elem.type);
                            Op aNewArray = JavaOps.insns(new TypeInsnNode(Opcodes.ANEWARRAY, elemType.getInternalName()));
                            Var elemV = ib.insert(aNewArray.insn(ib.insert(elemLen, "elemsz")),
                                    "elem");
                            JavaExts.JavaField field = new JavaExts.JavaField(jClass, "elem" + elemIdx,
                                    "[" + elemType.getDescriptor(),
                                    false);
                            jClass.fields.add(field);
                            Var toAssign;
                            if (elem.offset == null && elem.passive) {
                                toAssign = elemV;
                            } else {
                                toAssign = ib.insert(aNewArray.insn(ib.insert(CommonOps.constant(0), "0")), "empty");
                            }
                            ib.insert(JavaOps.PUT_FIELD.create(field).insn(IRUtils.getThis(ib), toAssign).assignTo());
                            elems.add(field);

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
                                for (ExprNode expr : elem.init) {
                                    Var j = ib.insert(CommonOps.constant(i), "j");
                                    Function exprFunc = funcMap.get(expr);
                                    Var f = ib.insert(new Inliner(ib)
                                                    .inline(convert.run(exprFunc), Collections.emptyList()),
                                            "f");
                                    module.functions.remove(exprFunc);
                                    ib.insert(JavaOps.ARRAY_SET.create()
                                            .insn(elemV, j, f)
                                            .assignTo());
                                    i++;
                                }
                            }

                            if (elem.offset != null) {
                                Function offsetFunc = funcMap.get(elem.offset);
                                Var offset = ib.insert(new Inliner(ib)
                                                .inline(convert.run(offsetFunc), Collections.emptyList()),
                                        "elem_offset");
                                module.functions.remove(offsetFunc);
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
                        for (DataNode data : node.datas) {
                            dataIdx++;
                            JavaExts.JavaField field = new JavaExts.JavaField(
                                    jClass,
                                    "data" + dataIdx,
                                    Type.getDescriptor(ByteBuffer.class),
                                    false
                            );
                            JavaExts.JavaMethod method = new JavaExts.JavaMethod(
                                    jClass,
                                    "initData" + dataIdx,
                                    "()V",
                                    JavaExts.JavaMethod.Kind.FINAL
                            );
                            Function func = new Function();
                            method.attachExt(JavaExts.METHOD_IMPL, func);
                            func.attachExt(JavaExts.FUNCTION_OWNER, jClass);
                            func.attachExt(JavaExts.FUNCTION_METHOD, method);
                            extrasModule.functions.add(func);
                            jClass.fields.add(field);
                            jClass.methods.add(method);
                            datas.add(field);

                            IRBuilder dIb = new IRBuilder(func, func.newBb());
                            Var dataV = dIb.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                                    IRUtils.BYTE_BUFFER_CLASS,
                                                    "allocate",
                                                    "(I)Ljava/nio/ByteBuffer;",
                                                    JavaExts.JavaMethod.Kind.STATIC
                                            ))
                                            .insn(dIb.insert(CommonOps.constant(data.init.length), "len")),
                                    "data");

                            Var slicedV = dIb.insert(JavaOps.INVOKE.create(new JavaExts.JavaMethod(
                                            IRUtils.BYTE_BUFFER_CLASS,
                                            "slice",
                                            "()Ljava/nio/ByteBuffer;",
                                            JavaExts.JavaMethod.Kind.VIRTUAL
                                    )).insn(dataV),
                                    "sliced");

                            IRUtils.fillAuto(data, dIb, slicedV);

                            dIb.insert(JavaOps.PUT_FIELD.create(field)
                                    .insn(IRUtils.getThis(dIb), dataV)
                                    .assignTo());
                            dIb.insertCtrl(CommonOps.RETURN.insn().jumpsTo());

                            ib.insert(JavaOps.INVOKE.create(method).insn(IRUtils.getThis(ib)).assignTo());

                            if (data.offset == null) continue;

                            MemoryConvention mem = memories.get(data.memory);

                            Function offsetFunc = funcMap.get(data.offset);
                            Var dataOffset = ib.insert(new Inliner(ib)
                                            .inline(convert.run(offsetFunc), Collections.emptyList()),
                                    "dataOffset");
                            module.functions.remove(offsetFunc);

                            mem.emitMemInit(ib, WasmOps.MEM_INIT
                                            .create(Pair.of(data.memory, dataIdx))
                                            .insn(dataOffset,
                                                    ib.insert(CommonOps.constant(0), "offset"),
                                                    ib.insert(CommonOps.constant(data.init.length), "len"))
                                            .assignTo(),
                                    ib.insert(JavaOps.GET_FIELD
                                            .create(datas.get(dataIdx)).insn(IRUtils.getThis(ib)), "data"));
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
                    JavaExts.JavaField elem = elems.get(index);
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

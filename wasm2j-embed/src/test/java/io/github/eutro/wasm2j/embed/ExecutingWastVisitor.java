package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.sexp.Parser;
import io.github.eutro.jwasm.sexp.wast.ActionVisitor;
import io.github.eutro.jwasm.sexp.wast.WastModuleVisitor;
import io.github.eutro.jwasm.sexp.wast.WastReader;
import io.github.eutro.jwasm.sexp.wast.WastVisitor;
import io.github.eutro.wasm2j.embed.internal.Utils;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.AssertionFailureBuilder.assertionFailure;
import static org.junit.jupiter.api.Assertions.*;

public class ExecutingWastVisitor extends WastVisitor {
    private Module lastModule;
    private boolean wasRefused;
    private ModuleRefusedException refusalReason;
    Map<String, ModuleInst> namedModuleInsts = new HashMap<>();
    private ModuleInst lastModuleInst;
    private final WebAssembly wasm;
    private final Store store;

    public ExecutingWastVisitor(WebAssembly wasm) {
        this.wasm = wasm;
        store = wasm.storeInit();
    }

    public ExecutingWastVisitor() {
        this(new WebAssembly());
    }

    ModuleInst getInst(@Nullable String name) {
        if (name == null) {
            if (wasRefused) throw new IllegalStateException();
            return lastModuleInst;
        } else {
            ModuleInst got = namedModuleInsts.get(name);
            if (got == null) throw new NoSuchElementException(name);
            return got;
        }
    }

    private final Map<String, ModuleInst> registered = new HashMap<>();

    {
        // https://github.com/WebAssembly/spec/tree/main/interpreter#spectest-host-module
        ExternVal globalI32 = ExternVal.global(new Global.BoxGlobal(0));
        ExternVal globalI64 = ExternVal.global(new Global.BoxGlobal(0L));
        ExternVal globalF32 = ExternVal.global(new Global.BoxGlobal(0F));
        ExternVal globalF64 = ExternVal.global(new Global.BoxGlobal(0D));
        Table.ArrayTable table = new Table.ArrayTable(10, 20);
        registered.put("spectest", name -> {
            switch (name) {
                case "global_i32":
                    return globalI32;
                case "global_i64":
                    return globalI64;
                case "global_f32":
                    return globalF32;
                case "global_f64":
                    return globalF64;
                case "table":
                    return ExternVal.table(table);
                case "memory":
                    return ExternVal.memory(new Memory.ByteBufferMemory(1, 2));
                case "print":
                    return ExternVal.func(() -> System.out.println());
                case "print_i32":
                case "print_i64":
                case "print_f32":
                case "print_f64":
                    return ExternVal.func((Object x) -> System.out.println(x));
                case "print_i32_f32":
                case "print_f64_f64":
                    return ExternVal.func(BiConsumer.class, (x, y) -> System.out.printf("%s %s", x, y));
            }
            return null;
        });
    }

    int tmc;

    @Override
    public @Nullable WastModuleVisitor visitModule(@Nullable String name) {
        tmc++;
        return new WastModuleVisitor() {
            @Override
            public void visitWatModule(Object module) {
                lastModule = wasm.moduleFromNode(Parser.parseModule(module));
            }

            @Override
            public void visitBinaryModule(Object module) {
                lastModule = wasm.moduleFromNode(Parser.parseBinaryModule(module));
            }

            @Override
            public void visitQuoteModule(Object module) {
                lastModule = wasm.moduleFromNode(Parser.parseQuoteModule(module));
            }

            @Override
            public void visitEnd() {
                try {
                    List<ModuleImport> imports = wasm.moduleImports(lastModule);
                    List<ExternVal> importVals = new ArrayList<>(imports.size());
                    for (ModuleImport theImport : imports) {
                        ModuleInst importModule = registered.get(theImport.module);
                        assertNotNull(importModule, theImport.module);
                        ExternVal theExport = wasm.instanceExport(importModule, theImport.name);
                        assertNotNull(theExport, theImport.name);
                        assertEquals(theExport.getType(), theImport.type, () -> theImport.module + ":" + theImport.name);
                        importVals.add(theExport);
                    }
                    lastModuleInst = wasm.moduleInstantiate(store, lastModule, importVals.toArray(new ExternVal[0]));
                    if (name != null) namedModuleInsts.put(name, lastModuleInst);
                    wasRefused = false;
                    refusalReason = null;
                } catch (ModuleRefusedException e) {
                    lastModuleInst = null;
                    wasRefused = true;
                    refusalReason = e;
                } catch (Throwable t) {
                    t.addSuppressed(new RuntimeException("in top module #" + tmc));
                    throw t;
                }
            }
        };
    }

    @Override
    public @Nullable ActionVisitor visitTopAction() {
        if (wasRefused) return null;
        return new ActionVisitor() {
            @Override
            public void visitInvoke(@Nullable String name, String string, Object... args) {
                try {
                    wasm.instanceExport(getInst(name), string)
                            .getAsFuncRaw()
                            .invokeWithArguments(args);
                } catch (Throwable t) {
                    throw Utils.rethrow(t);
                }
            }
        };
    }

    @Override
    public void visitRegister(String string, @Nullable String name) {
        if (wasRefused) throw new RuntimeException("Previous module was refused", refusalReason);
        registered.put(string, lastModuleInst);
    }

    int arc = 0;

    @Override
    public @Nullable ActionVisitor visitAssertReturn(Object... results) {
        arc++;
        if (wasRefused) return null;
        return new ActionVisitor() {
            @Override
            public void visitInvoke(@Nullable String name, String string, Object... args) {
                Object actualResults;
                try {
                    actualResults = wasm.instanceExport(getInst(name), string)
                            .getAsFuncRaw()
                            .invokeWithArguments(args);
                    switch (results.length) {
                        case 0:
                            assertNull(actualResults, name);
                            break;
                        case 1:
                            if (!WastReader.Checkable.check(results[0], actualResults)) {
                                assertionFailure()
                                        .expected(results[0])
                                        .actual(actualResults)
                                        .message(name)
                                        .buildAndThrow();
                            }
                            break;
                        default: {
                            if (!WastReader.Checkable.check(results, actualResults)) {
                                Object[] boxedResuls = new Object[Array.getLength(actualResults)];
                                for (int i = 0; i < boxedResuls.length; i++) {
                                    boxedResuls[i] = Array.get(actualResults, i);
                                }
                                assertArrayEquals(results, boxedResuls, name);
                            }
                        }
                    }
                } catch (Throwable t) {
                    Throwable th = t;
                    if (th.getSuppressed() == new NullPointerException().getSuppressed()) {
                        th = new RuntimeException(th);
                    }
                    th.addSuppressed(new RuntimeException(String.format("in assert_return #%d (%s.\"%s\"%s -> %s)",
                            arc,
                            lastModuleInst.getClass().getSimpleName(),
                            string,
                            Arrays.toString(args),
                            Arrays.toString(results))));
                    throw Utils.rethrow(th);
                }
            }
        };
    }
}

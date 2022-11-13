package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.sexp.Parser;
import io.github.eutro.jwasm.sexp.wast.ActionVisitor;
import io.github.eutro.jwasm.sexp.wast.WastModuleVisitor;
import io.github.eutro.jwasm.sexp.wast.WastReader;
import io.github.eutro.jwasm.sexp.wast.WastVisitor;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.AssertionFailureBuilder.assertionFailure;
import static org.junit.jupiter.api.Assertions.*;

public class ExecutingWastVisitor extends WastVisitor {
    private Module lastModule;
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

    private final Map<String, ModuleInst> registered = new HashMap<>();

    {
        // https://github.com/WebAssembly/spec/tree/main/interpreter#spectest-host-module
        registered.put("spectest", name -> {
            switch (name) {
                case "table":
                    return ExternVal.table();
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

    @Override
    public @Nullable WastModuleVisitor visitModule() {
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
            }
        };
    }

    @Override
    public @Nullable ActionVisitor visitTopAction() {
        return new ActionVisitor() {
            @Override
            public void visitInvoke(@Nullable String name, String string, Object... args) {
                try {
                    wasm.instanceExport(lastModuleInst, string)
                            .getAsFuncRaw()
                            .invokeWithArguments(args);
                } catch (RuntimeException | Error e) {
                    throw e;
                } catch (Throwable t) {
                    throw new RuntimeException(t);
                }
            }
        };
    }

    @Override
    public void visitRegister(String string, @Nullable String name) {
        registered.put(string, lastModuleInst);
    }

    int arc = 0;

    @Override
    public @Nullable ActionVisitor visitAssertReturn(Object... results) {
        arc++;
        return new ActionVisitor() {
            @Override
            public void visitInvoke(@Nullable String name, String string, Object... args) {
                Object actualResults;
                try {
                    actualResults = wasm.instanceExport(lastModuleInst, string)
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
                    t.addSuppressed(new RuntimeException(String.format("in assert_return #%d (%s.\"%s\"%s -> %s)",
                            arc,
                            lastModuleInst.getClass().getSimpleName(),
                            string,
                            Arrays.toString(args),
                            Arrays.toString(results))));
                    try {
                        throw t;
                    } catch (RuntimeException | Error e) {
                        throw e;
                    } catch (Throwable ignored) {
                        throw new RuntimeException(t);
                    }
                }
            }
        };
    }
}

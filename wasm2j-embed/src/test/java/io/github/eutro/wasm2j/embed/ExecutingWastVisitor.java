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
        ExternVal globalI32 = ExternVal.global(new Global.BoxGlobal(666));
        ExternVal globalI64 = ExternVal.global(new Global.BoxGlobal(666L));
        ExternVal globalF32 = ExternVal.global(new Global.BoxGlobal(666F));
        ExternVal globalF64 = ExternVal.global(new Global.BoxGlobal(666D));
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
        return new LinkingModuleVisitor() {
            @Override
            public void visitEnd() {
                try {
                    lastModuleInst = linkAndInst();
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

    @Override
    public @Nullable WastModuleVisitor visitAssertMalformed(String failure) {
        return null; // tested in jwasm
    }

    @Override
    public @Nullable WastModuleVisitor visitAssertInvalid(String failure) {
        return null; // tested in jwasm
    }

    @Override
    public @Nullable WastModuleVisitor visitAssertUnlinkable(String failure) {
        return visitAssertModuleTrap(failure);
    }

    int mtc;

    @Override
    public @Nullable WastModuleVisitor visitAssertModuleTrap(String failure) {
        mtc++;
        return new LinkingModuleVisitor() {
            @Override
            public void visitEnd() {
                try {
                    assertThrows(RuntimeException.class, this::linkAndInst, failure);
                } catch (Throwable t) {
                    t.addSuppressed(new RuntimeException("in assert_{trap,unlinkable} module #" + mtc));
                    throw t;
                }
            }
        };
    }

    @Override
    public @Nullable WastVisitor visitMetaScript(@Nullable String name) {
        throw new UnsupportedOperationException("visitMetaScript");
    }

    @Override
    public void visitMetaInput(@Nullable String name, String string) {
        throw new UnsupportedOperationException("visitMetaInput");
    }

    @Override
    public void visitMetaOutput(@Nullable String name, String string) {
        throw new UnsupportedOperationException("visitMetaOutput");
    }

    int arc = 0;

    @Override
    public @Nullable ActionVisitor visitAssertReturn(Object... results) {
        arc++;
        if (wasRefused) return null;
        return new ExecutingActionVisitor(arc, "assert_return", Arrays.toString(results)) {
            Object[] args;

            @Override
            public void visitInvoke(@Nullable String name, String string, Object... args) {
                super.visitInvoke(name, string, args);
                this.args = args;
            }

            @Override
            public void doChecks() throws Throwable {
                if (thrown != null) throw thrown;
                assertTrue(hasReturned);
                switch (results.length) {
                    case 0:
                        assertNull(returned, msg);
                        break;
                    case 1:
                        if (!WastReader.Checkable.check(results[0], returned)) {
                            assertionFailure()
                                    .expected(results[0])
                                    .actual(returned)
                                    .message(msg)
                                    .buildAndThrow();
                        }
                        break;
                    default: {
                        if (!WastReader.Checkable.check(results, returned)) {
                            Object[] boxedResuls = new Object[Array.getLength(returned)];
                            for (int i = 0; i < boxedResuls.length; i++) {
                                boxedResuls[i] = Array.get(returned, i);
                            }
                            assertArrayEquals(results, boxedResuls, msg);
                        }
                    }
                }
            }
        };
    }

    int atec = 0;

    @Override
    public @Nullable ActionVisitor visitAssertTrap(String failure) {
        atec++;
        return new ExecutingActionVisitor(atec, "assert_{trap,exhaustion}", "throws \"" + failure + '"') {
            @Override
            public void doChecks() {
                assertFalse(hasReturned, String.format("fail with \"%s\"", failure));
                System.err.printf("expected \"%s\", got \"%s\"\n", failure, thrown.getMessage());
            }
        };
    }

    @Override
    public @Nullable ActionVisitor visitAssertExhaustion(String failure) {
        return visitAssertTrap(failure);
    }

    private class LinkingModuleVisitor extends WastModuleVisitor {
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

        protected ModuleInst linkAndInst() {
            List<ModuleImport> imports = wasm.moduleImports(lastModule);
            List<ExternVal> importVals = new ArrayList<>(imports.size());
            for (ModuleImport theImport : imports) {
                ExternVal theExport;
                outer:
                {
                    inner:
                    {
                        ModuleInst importModule = registered.get(theImport.module);
                        if (importModule == null) break inner;
                        theExport = wasm.instanceExport(importModule, theImport.name);
                        if (theExport == null) break inner;
                        if (theExport.getType() != theImport.type) break inner;
                        break outer;
                    }
                    throw new LinkingException("unknown import");
                }
                importVals.add(theExport);
            }
            return wasm.moduleInstantiate(store, lastModule, importVals.toArray(new ExternVal[0]));
        }
    }

    private abstract class ExecutingActionVisitor extends ActionVisitor {
        Throwable thrown;
        Object returned;
        String msg;
        String msgExtra;
        boolean hasReturned;

        int index;
        String inWhat;
        String toWhat;

        public ExecutingActionVisitor(int index, String inWhat, String toWhat) {
            this.index = index;
            this.inWhat = inWhat;
            this.toWhat = toWhat;
        }

        ExternVal getExport(@Nullable String name, String string, ExternType type) {
            ExternVal export = wasm.instanceExport(getInst(name), string);
            msg = String.format("%s.\"%s\"", name, string);
            if (export == null) {
                throw new NoSuchElementException(msg);
            } else if (export.getType() != type) {
                throw new IllegalStateException(msg + " is not a " + type);
            }
            return export;
        }

        @Override
        public void visitInvoke(@Nullable String name, String string, Object... args) {
            msgExtra = Arrays.toString(args);
            try {
                returned = getExport(name, string, ExternType.FUNC).getAsFuncRaw().invokeWithArguments(args);
                hasReturned = true;
            } catch (Throwable t) {
                thrown = t;
            }
        }

        @Override
        public void visitGet(@Nullable String name, String string) {
            msgExtra = ".get";
            try {
                returned = getExport(name, string, ExternType.GLOBAL).getAsGlobal().get();
                hasReturned = true;
            } catch (Throwable t) {
                thrown = t;
            }
        }

        abstract void doChecks() throws Throwable;

        @Override
        public void visitEnd() {
            try {
                doChecks();
            } catch (Throwable t) {
                Throwable th = t;
                if (th.getSuppressed() == new NullPointerException().getSuppressed()) {
                    th = new RuntimeException(th);
                }
                th.addSuppressed(new RuntimeException(String.format("in %s #%d (%s/%s%s -> %s)",
                        inWhat,
                        arc,
                        lastModuleInst.getClass().getSimpleName(),
                        msg,
                        msgExtra,
                        toWhat)));
                throw Utils.rethrow(th);
            }
        }
    }

    private static class LinkingException extends RuntimeException {
        public LinkingException(Throwable t) {
            super(t);
        }

        public LinkingException(String msg) {
            super(msg);
        }
    }
}

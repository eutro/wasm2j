package io.github.eutro.wasm2j.embed;

import io.github.eutro.jwasm.sexp.WatParser;
import io.github.eutro.jwasm.sexp.wast.ActionVisitor;
import io.github.eutro.jwasm.sexp.wast.WastModuleVisitor;
import io.github.eutro.jwasm.sexp.wast.WastReader;
import io.github.eutro.jwasm.sexp.wast.WastVisitor;
import io.github.eutro.wasm2j.embed.internal.Utils;
import io.github.eutro.wasm2j.support.ExternType;
import io.github.eutro.wasm2j.support.ValType;
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
    Map<String, Instance> namedModuleInsts = new HashMap<>();
    private Instance lastInstance;
    private final WebAssembly wasm;
    private final Store store;

    public ExecutingWastVisitor(WebAssembly wasm) {
        this.wasm = wasm;
        store = wasm.storeInit();
    }

    public ExecutingWastVisitor() {
        this(new WebAssembly());
    }

    Instance getInst(@Nullable String name) {
        if (name == null) {
            if (wasRefused) throw new IllegalStateException();
            return lastInstance;
        } else {
            Instance got = namedModuleInsts.get(name);
            if (got == null) throw new NoSuchElementException(name);
            return got;
        }
    }

    private final Map<String, Instance> registered = new HashMap<>();

    {
        // https://github.com/WebAssembly/spec/tree/main/interpreter#spectest-host-module
        ExternVal globalI32 = new Global.BoxGlobal(ValType.I32, 666).setMut(false);
        ExternVal globalI64 = new Global.BoxGlobal(ValType.I64, 666L).setMut(false);
        ExternVal globalF32 = new Global.BoxGlobal(ValType.F32, 666F).setMut(false);
        ExternVal globalF64 = new Global.BoxGlobal(ValType.F64, 666D).setMut(false);
        Table.ArrayTable table = new Table.ArrayTable(10, 20, ValType.FUNCREF);
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
                    return table;
                case "memory":
                    return new Memory.ByteBufferMemory(1, 2);
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
                    lastInstance = linkAndInst();
                    if (name != null) namedModuleInsts.put(name, lastInstance);
                    wasRefused = false;
                    refusalReason = null;
                } catch (ModuleRefusedException e) {
                    lastInstance = null;
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
                            .getAsHandleRaw()
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
        registered.put(string, lastInstance);
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
            lastModule = wasm.moduleFromNode(WatParser.DEFAULT.parseModule(module));
        }

        @Override
        public void visitBinaryModule(Object module) {
            lastModule = wasm.moduleFromNode(WatParser.DEFAULT.parseBinaryModule(module));
        }

        @Override
        public void visitQuoteModule(Object module) {
            lastModule = wasm.moduleFromNode(WatParser.DEFAULT.parseQuoteModule(module));
        }

        protected Instance linkAndInst() {
            List<Import> imports = wasm.moduleImports(lastModule);
            List<ExternVal> importVals = new ArrayList<>(imports.size());
            for (Import theImport : imports) {
                ExternVal theExport;
                outer:
                {
                    inner:
                    {
                        Instance importModule = registered.get(theImport.module);
                        if (importModule == null) break inner;
                        theExport = wasm.instanceExport(importModule, theImport.name);
                        if (theExport == null) break inner;
                        if (!theImport.type.assignableFrom(theExport.getType())) break inner;
                        break outer;
                    }
                    throw new LinkingException("unknown import: " + theImport);
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

        ExternVal getExport(@Nullable String name, String string, ExternType.Kind kind) {
            ExternVal export = wasm.instanceExport(getInst(name), string);
            msg = String.format("%s.\"%s\"", name, string);
            if (export == null) {
                throw new NoSuchElementException(msg);
            } else if (!export.matchesType(null, kind)) {
                throw new IllegalStateException("Type mismatch, expected: "
                        + kind +
                        ", got: "
                        + export.getType().getKind());
            }
            return export;
        }

        @Override
        public void visitInvoke(@Nullable String name, String string, Object... args) {
            msgExtra = Arrays.toString(args);
            try {
                returned = getExport(name, string, ExternType.Kind.FUNC)
                        .getAsHandleRaw()
                        .invokeWithArguments(args);
                hasReturned = true;
            } catch (Throwable t) {
                thrown = t;
            }
        }

        @Override
        public void visitGet(@Nullable String name, String string) {
            msgExtra = ".get";
            try {
                returned = getExport(name, string, ExternType.Kind.GLOBAL)
                        .getAsGlobal()
                        .get();
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
                        lastInstance.getClass().getSimpleName(),
                        msg,
                        msgExtra,
                        toWhat)));
                throw Utils.rethrow(th);
            }
        }
    }

    private static class LinkingException extends RuntimeException {
        public LinkingException(String msg) {
            super(msg);
        }
    }
}

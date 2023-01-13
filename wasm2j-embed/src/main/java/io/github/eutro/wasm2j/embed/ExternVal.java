package io.github.eutro.wasm2j.embed;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public interface ExternVal {
    @NotNull ExternType getType();

    default boolean matchesType(@Nullable ExternType expected, ExternType.Kind kind) {
        ExternType type = getType();
        if (expected != null && !expected.assignableFrom(type)) {
            return false;
        }
        return type.getKind() == kind;
    }

    @GeneratedAccess
    default Table getAsTable() {
        if (this instanceof Table) return (Table) this;
        throw new IllegalArgumentException("Not a table");
    }

    @GeneratedAccess
    default Global getAsGlobal() {
        if (this instanceof Global) return (Global) this;
        throw new IllegalArgumentException("Not a global");
    }

    @GeneratedAccess
    default Memory getAsMemory() {
        if (this instanceof Memory) return (Memory) this;
        throw new IllegalArgumentException("Not a memory");
    }

    @GeneratedAccess
    default Func getAsFunc() {
        if (this instanceof Func) return (Func) this;
        throw new IllegalArgumentException("Not a func");
    }

    default MethodHandle getAsHandleRaw() {
        return getAsFunc().handle();
    }

    @GeneratedAccess
    default MethodHandle getAsHandle(MethodType type) {
        return getAsHandleRaw().asType(type);
    }

    static ExternVal func(MethodHandle handle) {
        return Func.HandleFunc.create(null, handle);
    }

    static <F> ExternVal func(Class<? super F> functionalInterfaceClass, F f) {
        Method[] methods = functionalInterfaceClass.getMethods();
        Method abstractMethod = null;
        for (Method method : methods) {
            if (Modifier.isAbstract(method.getModifiers())) {
                if (abstractMethod != null) {
                    abstractMethod = null;
                    break;
                }
                abstractMethod = method;
            }
        }
        if (abstractMethod == null) {
            throw new IllegalArgumentException("Not a functional interface: " + f);
        }
        try {
            return func(MethodHandles.lookup().unreflect(abstractMethod).bindTo(f));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // @formatter:off
    static ExternVal func(Fn.V f) { return Fn.func(f, Fn.V.class); }
    static ExternVal func(Fn.I f) { return Fn.func(f, Fn.I.class); }
    static ExternVal func(Fn.L f) { return Fn.func(f, Fn.L.class); }
    static ExternVal func(Fn.F f) { return Fn.func(f, Fn.F.class); }
    static ExternVal func(Fn.D f) { return Fn.func(f, Fn.D.class); }
    static ExternVal func(Fn.O f) { return Fn.func(f, Fn.O.class); }
    static ExternVal func(Fn.IV f) { return Fn.func(f, Fn.IV.class); }
    static ExternVal func(Fn.II f) { return Fn.func(f, Fn.II.class); }
    static ExternVal func(Fn.IL f) { return Fn.func(f, Fn.IL.class); }
    static ExternVal func(Fn.IF f) { return Fn.func(f, Fn.IF.class); }
    static ExternVal func(Fn.ID f) { return Fn.func(f, Fn.ID.class); }
    static ExternVal func(Fn.IO f) { return Fn.func(f, Fn.IO.class); }
    static ExternVal func(Fn.LV f) { return Fn.func(f, Fn.LV.class); }
    static ExternVal func(Fn.LI f) { return Fn.func(f, Fn.LI.class); }
    static ExternVal func(Fn.LL f) { return Fn.func(f, Fn.LL.class); }
    static ExternVal func(Fn.LF f) { return Fn.func(f, Fn.LF.class); }
    static ExternVal func(Fn.LD f) { return Fn.func(f, Fn.LD.class); }
    static ExternVal func(Fn.LO f) { return Fn.func(f, Fn.LO.class); }
    static ExternVal func(Fn.FV f) { return Fn.func(f, Fn.FV.class); }
    static ExternVal func(Fn.FI f) { return Fn.func(f, Fn.FI.class); }
    static ExternVal func(Fn.FL f) { return Fn.func(f, Fn.FL.class); }
    static ExternVal func(Fn.FF f) { return Fn.func(f, Fn.FF.class); }
    static ExternVal func(Fn.FD f) { return Fn.func(f, Fn.FD.class); }
    static ExternVal func(Fn.FO f) { return Fn.func(f, Fn.FO.class); }
    static ExternVal func(Fn.DV f) { return Fn.func(f, Fn.DV.class); }
    static ExternVal func(Fn.DI f) { return Fn.func(f, Fn.DI.class); }
    static ExternVal func(Fn.DL f) { return Fn.func(f, Fn.DL.class); }
    static ExternVal func(Fn.DF f) { return Fn.func(f, Fn.DF.class); }
    static ExternVal func(Fn.DD f) { return Fn.func(f, Fn.DD.class); }
    static ExternVal func(Fn.DO f) { return Fn.func(f, Fn.DO.class); }
    static ExternVal func(Fn.OV f) { return Fn.func(f, Fn.OV.class); }
    static ExternVal func(Fn.OI f) { return Fn.func(f, Fn.OI.class); }
    static ExternVal func(Fn.OL f) { return Fn.func(f, Fn.OL.class); }
    static ExternVal func(Fn.OF f) { return Fn.func(f, Fn.OF.class); }
    static ExternVal func(Fn.OD f) { return Fn.func(f, Fn.OD.class); }
    static ExternVal func(Fn.OO f) { return Fn.func(f, Fn.OO.class); }
    static ExternVal func(Fn.OsV f) { return Fn.func(f, Fn.OsV.class); }
    static ExternVal func(Fn.OsI f) { return Fn.func(f, Fn.OsI.class); }
    static ExternVal func(Fn.OsL f) { return Fn.func(f, Fn.OsL.class); }
    static ExternVal func(Fn.OsF f) { return Fn.func(f, Fn.OsF.class); }
    static ExternVal func(Fn.OsD f) { return Fn.func(f, Fn.OsD.class); }
    static ExternVal func(Fn.OsO f) { return Fn.func(f, Fn.OsO.class); }

    interface Fn {
        interface V {void call();}
        interface I {int call();}
        interface L {long call();}
        interface F {float call();}
        interface D {double call();}
        interface O {Object call();}

        interface IV {void call(int x);}
        interface II {int call(int x);}
        interface IL {long call(int x);}
        interface IF {float call(int x);}
        interface ID {double call(int x);}
        interface IO {Object call(int x);}
        interface LV {void call(long x);}
        interface LI {int call(long x);}
        interface LL {long call(long x);}
        interface LF {float call(long x);}
        interface LD {double call(long x);}
        interface LO {Object call(long x);}
        interface FV {void call(float x);}
        interface FI {int call(float x);}
        interface FL {long call(float x);}
        interface FF {float call(float x);}
        interface FD {double call(float x);}
        interface FO {Object call(float x);}
        interface DV {void call(double x);}
        interface DI {int call(double x);}
        interface DL {long call(double x);}
        interface DF {float call(double x);}
        interface DD {double call(double x);}
        interface DO {Object call(double x);}
        interface OV {void call(Object x);}
        interface OI {int call(Object x);}
        interface OL {long call(Object x);}
        interface OF {float call(Object x);}
        interface OD {double call(Object x);}
        interface OO {Object call(Object x);}

        interface OsV {void call(Object... xs);}
        interface OsI {int call(Object... xs);}
        interface OsL {long call(Object... xs);}
        interface OsF {float call(Object... xs);}
        interface OsD {double call(Object... xs);}
        interface OsO {Object call(Object... xs);}
        // @formatter:on

        static ExternVal func(Object f, Class<?> itf) {
            try {
                return ExternVal.func(MethodHandles.publicLookup()
                        .unreflect(itf.getMethods()[0])
                        .bindTo(f));
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

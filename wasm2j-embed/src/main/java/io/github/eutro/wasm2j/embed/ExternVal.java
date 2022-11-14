package io.github.eutro.wasm2j.embed;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public final class ExternVal {
    private final ExternType type;
    private final Object value;

    ExternVal(ExternType type, Object value) {
        this.type = type;
        this.value = value;
    }

    public ExternType getType() {
        return type;
    }

    private void checkType(ExternType expected) {
        if (type != expected) {
            throw new IllegalArgumentException("Not a " + expected);
        }
    }

    public static ExternVal table(Table table) {
        return new ExternVal(ExternType.TABLE, table);
    }

    public Table getAsTable() {
        checkType(ExternType.TABLE);
        return (Table) value;
    }

    public MethodHandle getAsFuncRaw() {
        checkType(ExternType.FUNC);
        return (MethodHandle) value;
    }

    public MethodHandle getAsFunc(MethodType type) {
        return getAsFuncRaw().asType(type);
    }

    public static ExternVal func(MethodHandle handle) {
        return new ExternVal(ExternType.FUNC, handle);
    }

    public static <F> ExternVal func(Class<? super F> functionalInterfaceClass, F f) {
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
    public static ExternVal func(Func.V f) { return func(f, Func.V.class); }
    public static ExternVal func(Func.I f) { return func(f, Func.I.class); }
    public static ExternVal func(Func.L f) { return func(f, Func.L.class); }
    public static ExternVal func(Func.F f) { return func(f, Func.F.class); }
    public static ExternVal func(Func.D f) { return func(f, Func.D.class); }
    public static ExternVal func(Func.O f) { return func(f, Func.O.class); }
    public static ExternVal func(Func.IV f) { return func(f, Func.IV.class); }
    public static ExternVal func(Func.II f) { return func(f, Func.II.class); }
    public static ExternVal func(Func.IL f) { return func(f, Func.IL.class); }
    public static ExternVal func(Func.IF f) { return func(f, Func.IF.class); }
    public static ExternVal func(Func.ID f) { return func(f, Func.ID.class); }
    public static ExternVal func(Func.IO f) { return func(f, Func.IO.class); }
    public static ExternVal func(Func.LV f) { return func(f, Func.LV.class); }
    public static ExternVal func(Func.LI f) { return func(f, Func.LI.class); }
    public static ExternVal func(Func.LL f) { return func(f, Func.LL.class); }
    public static ExternVal func(Func.LF f) { return func(f, Func.LF.class); }
    public static ExternVal func(Func.LD f) { return func(f, Func.LD.class); }
    public static ExternVal func(Func.LO f) { return func(f, Func.LO.class); }
    public static ExternVal func(Func.FV f) { return func(f, Func.FV.class); }
    public static ExternVal func(Func.FI f) { return func(f, Func.FI.class); }
    public static ExternVal func(Func.FL f) { return func(f, Func.FL.class); }
    public static ExternVal func(Func.FF f) { return func(f, Func.FF.class); }
    public static ExternVal func(Func.FD f) { return func(f, Func.FD.class); }
    public static ExternVal func(Func.FO f) { return func(f, Func.FO.class); }
    public static ExternVal func(Func.DV f) { return func(f, Func.DV.class); }
    public static ExternVal func(Func.DI f) { return func(f, Func.DI.class); }
    public static ExternVal func(Func.DL f) { return func(f, Func.DL.class); }
    public static ExternVal func(Func.DF f) { return func(f, Func.DF.class); }
    public static ExternVal func(Func.DD f) { return func(f, Func.DD.class); }
    public static ExternVal func(Func.DO f) { return func(f, Func.DO.class); }
    public static ExternVal func(Func.OV f) { return func(f, Func.OV.class); }
    public static ExternVal func(Func.OI f) { return func(f, Func.OI.class); }
    public static ExternVal func(Func.OL f) { return func(f, Func.OL.class); }
    public static ExternVal func(Func.OF f) { return func(f, Func.OF.class); }
    public static ExternVal func(Func.OD f) { return func(f, Func.OD.class); }
    public static ExternVal func(Func.OO f) { return func(f, Func.OO.class); }
    public static ExternVal func(Func.OsV f) { return func(f, Func.OsV.class); }
    public static ExternVal func(Func.OsI f) { return func(f, Func.OsI.class); }
    public static ExternVal func(Func.OsL f) { return func(f, Func.OsL.class); }
    public static ExternVal func(Func.OsF f) { return func(f, Func.OsF.class); }
    public static ExternVal func(Func.OsD f) { return func(f, Func.OsD.class); }
    public static ExternVal func(Func.OsO f) { return func(f, Func.OsO.class); }

    public interface Func {
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

        interface OsV {void call(Object[] xs);}
        interface OsI {int call(Object[] xs);}
        interface OsL {long call(Object[] xs);}
        interface OsF {float call(Object[] xs);}
        interface OsD {double call(Object[] xs);}
        interface OsO {Object call(Object[] xs);}
        // @formatter:on
    }

    private static ExternVal func(Object f, Class<?> itf) {
        try {
            return func(MethodHandles.publicLookup()
                    .unreflect(itf.getMethods()[0])
                    .bindTo(f));
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}

package io.github.eutro.wasm2j.ssa;

import io.github.eutro.wasm2j.ext.ExtHolder;
import io.github.eutro.wasm2j.util.Pair;
import org.jetbrains.annotations.Contract;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents the IR of a Java class.
 */
public class JClass extends ExtHolder {
    /**
     * The
     * <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.2.1">internal name</a>
     * of the class.
     */
    public String name; // internal name
    /**
     * The access modifiers bit mask of the class.
     *
     * @see Class#getModifiers()
     */
    public int access;
    /**
     * The list of methods in the class.
     */
    public final List<JavaMethod> methods = new ArrayList<>();
    /**
     * The list of fields in the class.
     */
    public final List<JavaField> fields = new ArrayList<>();

    /**
     * Construct a Java class with the given (internal) name and access modifiers.
     *
     * @param name   The internal name.
     * @param access The access modifiers.
     */
    public JClass(String name, int access) {
        this.name = name;
        this.access = access;
    }

    /**
     * Construct a Java class with default {@code public class} access modifiers.
     *
     * @param name The internal name.
     */
    public JClass(String name) {
        this(name, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER);
    }

    /**
     * Create an empty Java class with the same name and access modifiers as
     * a class in the current JVM.
     *
     * @param clazz The class.
     * @return The new Java class.
     */
    @Contract(pure = true)
    public static JClass emptyFromJava(Class<?> clazz) {
        JClass jClass = new JClass(Type.getInternalName(clazz));
        jClass.access = clazz.getModifiers();
        return jClass;
    }

    /**
     * Get the ASM {@link Type} of this class.
     *
     * @return The type.
     */
    public Type getType() {
        return Type.getObjectType(name);
    }

    @Override
    public String toString() {
        return name.replace('/', '.');
    }

    /**
     * Represents a type that can be loaded as a constant method handle.
     */
    public interface Handlable {
        /**
         * Get the ASM {@link Handle} to this.
         *
         * @return ASM {@link Handle} to this.
         */
        Handle getHandle();
    }

    private SoftReference<Map<Pair<String, List<Class<?>>>, JavaMethod>> methodCache = new SoftReference<>(null);

    /**
     * Look up a method in this class, when a class with this name exists in
     * the current JVM. The results will be cached.
     *
     * @param name           The name of the method.
     * @param parameterTypes The parameters of the method.
     * @return The looked up method.
     * @see Class#getMethod(String, Class[])
     */
    public JavaMethod lookupMethod(String name, Class<?>... parameterTypes) {
        Map<Pair<String, List<Class<?>>>, JavaMethod> cache = methodCache.get();
        if (cache == null) {
            methodCache = new SoftReference<>(cache = new ConcurrentHashMap<>());
        }
        return cache.computeIfAbsent(Pair.of(name, Arrays.asList(parameterTypes)), $ -> {
            try {
                Class<?> clazz = Class.forName(this.name.replace('/', '.'));
                Method method = clazz.getMethod(name, parameterTypes);
                return JavaMethod.fromJava(this, method);
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException(e);
            }
        });
    }

    /**
     * A Java method in a Java class.
     */
    public static class JavaMethod extends ExtHolder implements Handlable {
        /**
         * The owner of the method.
         */
        public JClass owner;
        /**
         * The name of the method.
         */
        public String name;
        /**
         * The access modifiers of the method.
         *
         * @see Method#getModifiers()
         */
        public int access;

        private List<Type> paramTys;
        private Type returnTy;

        /**
         * Construct a method with the given owner, name, type, and kind.
         *
         * @param owner      The owner.
         * @param name       The name.
         * @param descriptor The descriptor of the type.
         * @param access     The access modifiers of the type.
         */
        public JavaMethod(JClass owner, String name, String descriptor, int access) {
            this.owner = owner;
            this.name = name;
            this.setDescriptor(descriptor);
            this.access = access;
        }

        static JavaMethod fromJava(JClass owner, Method method) {
            return new JavaMethod(
                    owner,
                    method.getName(),
                    Type.getMethodDescriptor(method),
                    method.getModifiers()
            );
        }

        /**
         * Get the list of parameter types of the method.
         *
         * @return The parameter types.
         */
        public List<Type> getParamTys() {
            return paramTys;
        }

        /**
         * Get the type of this method as a method descriptor.
         *
         * @return The descriptor of the method.
         */
        public String getDescriptor() {
            return Type.getMethodDescriptor(
                    returnTy,
                    paramTys.toArray(new Type[0])
            );
        }

        /**
         * Get the return type of the method.
         *
         * @return The return type of the method.
         */
        public Type getReturnTy() {
            return returnTy;
        }

        /**
         * Set the return type of the method.
         *
         * @param returnTy The return type of the method.
         */
        public void setReturnTy(Type returnTy) {
            this.returnTy = returnTy;
        }

        /**
         * Set the type of the method fully to the type described by the method descriptor.
         *
         * @param descriptor The method descriptor.
         */
        public void setDescriptor(String descriptor) {
            returnTy = Type.getReturnType(descriptor);
            paramTys = new ArrayList<>(Arrays.asList(Type.getArgumentTypes(descriptor)));
        }

        @Override
        public Handle getHandle() {
            return new Handle(getHandleType(), owner.name, name, getDescriptor(), isInterface());
        }

        private int getHandleType() {
            int invokeType;
            if (isStatic()) {
                invokeType = Opcodes.H_INVOKESTATIC;
            } else if (isSpecial()) {
                invokeType = Opcodes.H_INVOKESPECIAL;
            } else if (isInterface()) {
                invokeType = Opcodes.H_INVOKEINTERFACE;
            } else {
                invokeType = Opcodes.H_INVOKEVIRTUAL;
            }
            return invokeType;
        }

        public int getInvokeOpcode() {
            int invokeType;
            if (isStatic()) {
                invokeType = Opcodes.INVOKESTATIC;
            } else if (isSpecial()) {
                invokeType = Opcodes.INVOKESPECIAL;
            } else if (isInterface()) {
                invokeType = Opcodes.INVOKEINTERFACE;
            } else {
                invokeType = Opcodes.INVOKEVIRTUAL;
            }
            return invokeType;
        }

        public boolean isInterface() {
            return Modifier.isInterface(owner.access);
        }

        public boolean isStatic() {
            return Modifier.isStatic(access);
        }

        public int getAccess() {
            return access;
        }

        public boolean isSpecial() {
            return !isStatic() && Modifier.isPrivate(access) || name.equals("<init>");
        }

        @Override
        public String toString() {
            return String.format("%s %s.%s%s",
                    isStatic() ? "static" :
                            isInterface() ? "interface" :
                                    isSpecial() ? "special" :
                                            "virtual",
                    owner,
                    name,
                    getDescriptor());
        }
    }

    public static class JavaField extends ExtHolder {
        public JClass owner;
        public String name, descriptor;

        public int access;

        public JavaField(JClass owner, String name, String descriptor, boolean isStatic) {
            this(owner, name, descriptor, Opcodes.ACC_PRIVATE | (isStatic ? Opcodes.ACC_STATIC : 0));
        }

        public JavaField(JClass owner, String name, String descriptor, int access) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.access = access;
        }

        public static JavaField fromJava(JClass jClass, Field field) {
            return new JavaField(
                    jClass,
                    field.getName(),
                    Type.getDescriptor(field.getType()),
                    Modifier.isStatic(field.getModifiers())
            );
        }

        public static JavaField fromJava(Class<?> clazz, String name) {
            try {
                return fromJava(new JClass(Type.getInternalName(clazz)), clazz.getField(name));
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean isStatic() {
            return Modifier.isStatic(access);
        }

        public Handlable getter() {
            return () -> new Handle(isStatic() ? Opcodes.H_GETSTATIC : Opcodes.H_GETFIELD,
                    owner.name, name, descriptor, false);
        }

        public Handlable setter() {
            return () -> new Handle(isStatic() ? Opcodes.H_PUTSTATIC : Opcodes.H_PUTFIELD,
                    owner.name, name, descriptor, false);
        }

        @Override
        public String toString() {
            return String.format("%s%s.%s %s",
                    isStatic() ? "static " : "",
                    owner,
                    name,
                    descriptor);
        }
    }
}

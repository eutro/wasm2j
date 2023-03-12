package io.github.eutro.wasm2j.core.ssa;

import io.github.eutro.wasm2j.core.ext.ExtHolder;
import io.github.eutro.wasm2j.core.util.Pair;
import org.jetbrains.annotations.Contract;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.ref.SoftReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
    public interface Handleable {
        /**
         * Get the ASM {@link Handle} to this.
         *
         * @return ASM {@link Handle} to this.
         */
        Handle getHandle();
    }

    private SoftReference<Map<Pair<String, List<Class<?>>>, JavaMethod>> methodCache = new SoftReference<>(null);

    /**
     * Look up a method in this class by {@link java.lang.reflect reflection},
     * when a class with this name exists in the current JVM.
     * <p>
     * The results will be cached in this instance, but not necessarily permanently.
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
     * A method in a {@link JClass Java class}.
     * <p>
     * Note that constructors are also methods, but with the special name {@code <init>},
     * and thus are also represented by {@link JavaMethod}s.
     */
    public static class JavaMethod extends ExtHolder implements Handleable {
        /**
         * The owner of the method.
         * <p>
         * Note: this {@link JavaMethod method} does not necessarily have to be in {@link JClass#methods owner.methods}.
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
         * Construct a method with the given owner, name, type, and access modifiers.
         *
         * @param owner      The owner.
         * @param name       The name.
         * @param descriptor The <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3">descriptor</a>
         *                   of the method type.
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
         * Get the type of this method as a
         * <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3">method descriptor</a>.
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
         * Set the type of the method fully to the type described by the
         * <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3">method descriptor</a>.
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

        /**
         * Get the Java opcode required to invoke this method.
         *
         * @return One of
         * {@link Opcodes#INVOKESTATIC},
         * {@link Opcodes#INVOKEVIRTUAL},
         * {@link Opcodes#INVOKEINTERFACE}, or
         * {@link Opcodes#INVOKESPECIAL}.
         */
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

        /**
         * Get whether the owner of this method is an interface.
         * <p>
         * This is necessary because the JVM distinguishes between method references in
         * interfaces and those in non-interface classes.
         * <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.3">JVMS 4.4.2</a>
         *
         * @return Whether the owner of this method is an interface.
         */
        public boolean isInterface() {
            return Modifier.isInterface(owner.access);
        }

        /**
         * Get whether this method is static.
         *
         * @return Whether the access modifiers of this method declare it as static.
         */
        public boolean isStatic() {
            return Modifier.isStatic(access);
        }

        /**
         * Get whether the method should be invoked with an {@code invokespecial} instruction.
         * This is the case when the method is not static, but is private, or it is {@code <init>}.
         *
         * @return Whether the method should be invoked with {@code invokespecial}.
         */
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

    /**
     * A field in a {@link JClass Java class}.
     */
    public static class JavaField extends ExtHolder {
        /**
         * The owner of the method.
         * <p>
         * Note: this {@link JavaField method} does not necessarily have to be in {@link JClass#fields owner.fields}.
         */
        public JClass owner;
        /**
         * The name of the field.
         */
        public String name;
        /**
         * The
         * <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.2">descriptor</a>
         * of the field.
         */
        public String descriptor;
        /**
         * The access modifiers of the field.
         *
         * @see Field#getModifiers()
         */
        public int access;

        /**
         * Construct a new private field.
         *
         * @param owner      The owner.
         * @param name       The name.
         * @param descriptor The <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.2">field descriptor</a>.
         * @param isStatic   Whether this field is static.
         */
        public JavaField(JClass owner, String name, String descriptor, boolean isStatic) {
            this(owner, name, descriptor, Opcodes.ACC_PRIVATE | (isStatic ? Opcodes.ACC_STATIC : 0));
        }

        /**
         * Construct a new field with the given access modifiers.
         *
         * @param owner      The owner.
         * @param name       The name.
         * @param descriptor The <a href="https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.3.2">field descriptor</a>.
         * @param access     The access modifiers.
         */
        public JavaField(JClass owner, String name, String descriptor, int access) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.access = access;
        }

        static JavaField fromJava(JClass jClass, Field field) {
            return new JavaField(
                    jClass,
                    field.getName(),
                    Type.getDescriptor(field.getType()),
                    Modifier.isStatic(field.getModifiers())
            );
        }

        /**
         * Look up a field in a class by {@link java.lang.reflect reflection}.
         * <p>
         * This will create a new {@link JClass} and a new {@link JavaField} for it.
         *
         * @param clazz The class to look it up in.
         * @param name The name of the field.
         * @return The found field.
         */
        public static JavaField fromJava(Class<?> clazz, String name) {
            try {
                return fromJava(JClass.emptyFromJava(clazz), clazz.getField(name));
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Get whether this field is static.
         *
         * @return Whether the field is static.
         */
        public boolean isStatic() {
            return Modifier.isStatic(access);
        }

        /**
         * Returns a {@link Handleable} that resolves to a getter handle to the field.
         *
         * @return A getter {@link Handleable}.
         */
        public Handleable getter() {
            return () -> new Handle(isStatic() ? Opcodes.H_GETSTATIC : Opcodes.H_GETFIELD,
                    owner.name, name, descriptor, false);
        }

        /**
         * Returns a {@link Handleable} that resolves to a setter handle to the field.
         *
         * @return A setter {@link Handleable}.
         */
        public Handleable setter() {
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

package io.github.eutro.wasm2j.ext;

import io.github.eutro.wasm2j.intrinsics.IntrinsicImpl;
import io.github.eutro.wasm2j.ssa.Function;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodNode;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class JavaExts {
    public static final Ext<Function> METHOD_IMPL = Ext.create(Function.class);
    public static final Ext<MethodNode> METHOD_NATIVE_IMPL = Ext.create(MethodNode.class);
    public static final Ext<Type> TYPE = Ext.create(Type.class);
    public static final Ext<JavaMethod> FUNCTION_METHOD = Ext.create(JavaMethod.class);
    public static final Ext<JavaClass> FUNCTION_OWNER = Ext.create(JavaClass.class);

    public static final Ext<Map<IntrinsicImpl, JavaMethod>> ATTACHED_INTRINSICS = Ext.create(Map.class);

    public static Type BOTTOM_TYPE = Type.getType(Void.class);

    public static class JavaClass extends ExtHolder {
        public String name; // internal name
        public int access;
        public List<JavaMethod> methods = new ArrayList<>();
        public List<JavaField> fields = new ArrayList<>();

        public JavaClass(String name, int access) {
            this.name = name;
            this.access = access;
        }

        public JavaClass(String name) {
            this(name, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER);
        }

        public static JavaClass fromJava(Class<?> clazz) {
            JavaExts.JavaClass jClass = new JavaExts.JavaClass(Type.getInternalName(clazz));
            for (Method method : clazz.getMethods()) {
                if (method.isSynthetic()) continue;
                jClass.methods.add(JavaMethod.fromJava(jClass, method));
            }
            for (Field field : clazz.getFields()) {
                if (field.isSynthetic()) continue;
                jClass.fields.add(JavaField.fromJava(jClass, field));
            }
            return jClass;
        }

        public Type getType() {
            return Type.getObjectType(name);
        }

        @Override
        public String toString() {
            return name.replace('/', '.');
        }
    }

    public interface Handlable {
        Handle getHandle();
    }

    public static class JavaMethod extends ExtHolder implements Handlable {
        public JavaClass owner;
        public String name;
        public Kind kind;

        private List<Type> paramTys;
        private Type returnTy;

        public JavaMethod(JavaClass owner, String name, String descriptor, Kind kind) {
            this.owner = owner;
            this.name = name;
            this.setDescriptor(descriptor);
            this.kind = kind;
        }

        public static JavaMethod fromJava(JavaClass owner, Method method) {
            return new JavaMethod(
                    owner,
                    method.getName(),
                    Type.getMethodDescriptor(method),
                    Modifier.isStatic(method.getModifiers()) ? Kind.STATIC :
                            Modifier.isInterface(owner.access) ? Kind.INTERFACE : Kind.VIRTUAL
            );
        }

        public static JavaMethod fromJava(Class<?> owner, String name, Class<?>... parameterTypes) {
            try {
                return fromJava(new JavaClass(Type.getInternalName(owner), owner.getModifiers()),
                        owner.getMethod(name, parameterTypes));
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            }
        }

        public List<Type> getParamTys() {
            return paramTys;
        }

        public void setParamTys(List<Type> tys) {
            paramTys = new ArrayList<>(tys);
        }

        public String getDescriptor() {
            return Type.getMethodDescriptor(
                    returnTy,
                    paramTys.toArray(new Type[0])
            );
        }

        public Type getReturnTy() {
            return returnTy;
        }

        public void setDescriptor(String descriptor) {
            returnTy = Type.getReturnType(descriptor);
            paramTys = new ArrayList<>(Arrays.asList(Type.getArgumentTypes(descriptor)));
        }

        @Override
        public Handle getHandle() {
            return new Handle(kind.handleType, owner.name, name, getDescriptor(), kind.isInterface());
        }

        public enum Kind {
            STATIC(Opcodes.ACC_STATIC | Opcodes.ACC_PUBLIC, Opcodes.INVOKESTATIC, Opcodes.H_INVOKESTATIC),
            STATIC_PRIVATE(Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE, Opcodes.INVOKESTATIC, Opcodes.H_INVOKESTATIC),
            VIRTUAL(Opcodes.ACC_PUBLIC, Opcodes.INVOKEVIRTUAL, Opcodes.H_INVOKEVIRTUAL),
            FINAL(Opcodes.ACC_PRIVATE, Opcodes.INVOKESPECIAL, Opcodes.H_INVOKESPECIAL),
            ABSTRACT(Opcodes.ACC_ABSTRACT | Opcodes.ACC_PROTECTED, Opcodes.INVOKEVIRTUAL, Opcodes.H_INVOKEVIRTUAL),
            INTERFACE(Opcodes.ACC_PUBLIC, Opcodes.INVOKEINTERFACE, Opcodes.H_INVOKEINTERFACE);

            public final int access;
            public final int opcode;
            public final int handleType;

            Kind(int access, int opcode, int handleType) {
                this.access = access;
                this.opcode = opcode;
                this.handleType = handleType;
            }

            public boolean isInterface() {
                return this == INTERFACE;
            }

            public boolean isStatic() {
                return this == STATIC || this == STATIC_PRIVATE;
            }
        }

        @Override
        public String toString() {
            return String.format("%s %s.%s%s",
                    kind.toString().toLowerCase(Locale.ROOT),
                    owner,
                    name,
                    getDescriptor());
        }
    }

    public static class JavaField extends ExtHolder {
        public JavaClass owner;
        public String name, descriptor;
        public boolean isStatic;
        public int otherAccess = Opcodes.ACC_PRIVATE;

        public JavaField(JavaClass owner, String name, String descriptor, boolean isStatic) {
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.isStatic = isStatic;
        }

        public static JavaField fromJava(JavaClass jClass, Field field) {
            return new JavaField(
                    jClass,
                    field.getName(),
                    Type.getDescriptor(field.getType()),
                    Modifier.isStatic(field.getModifiers())
            );
        }

        public static JavaField fromJava(Class<?> clazz, String name) {
            try {
                return fromJava(new JavaClass(Type.getInternalName(clazz)), clazz.getField(name));
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        public Handlable getter() {
            return () -> new Handle(isStatic ? Opcodes.H_GETSTATIC : Opcodes.H_GETFIELD,
                    owner.name, name, descriptor, false);
        }

        public Handlable setter() {
            return () -> new Handle(isStatic ? Opcodes.H_PUTSTATIC : Opcodes.H_PUTFIELD,
                    owner.name, name, descriptor, false);
        }

        @Override
        public String toString() {
            return String.format("%s%s.%s %s",
                    isStatic ? "static " : "",
                    owner,
                    name,
                    descriptor);
        }
    }

    public static final Ext<JavaClass> JAVA_CLASS = Ext.create(JavaClass.class);
}

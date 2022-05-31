package io.github.eutro.wasm2j.test;

import io.github.eutro.jwasm.ModuleReader;
import io.github.eutro.jwasm.tree.ModuleNode;
import io.github.eutro.wasm2j.ModuleAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AocTest extends ModuleAdapterTest {
    public static class IOImpl {
        public InputStream is;
        public OutputStream os;

        public int stdinReadByte() throws IOException {
            if (is == null) {
                return -1;
            } else {
                return is.read();
            }
        }

        public void stdoutWriteByte(int b) throws IOException {
            if (os != null) {
                os.write(b);
            }
        }
    }

    private static class Matcher {
        private final Object aoc;
        private final IOImpl io;

        private Matcher(Object aoc) throws Throwable {
            this.aoc = aoc;
            io = new IOImpl();
            aoc.getClass().getField("ioImpl").set(aoc, io);
        }

        public void assertMatches(String name, String input, String output) throws Throwable {
            Method dayMethod = aoc.getClass().getMethod(name);
            io.is = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            io.os = baos;
            dayMethod.invoke(aoc);
            assertEquals(output, baos.toString());
        }
    }

    private Matcher m;

    @BeforeEach
    void setUp() throws Throwable {
        ModuleNode mn = new ModuleNode();
        try (InputStream is = ModuleAdapter.class.getResourceAsStream("/aoc_bg.wasm")) {
            ModuleReader.fromInputStream(is).accept(mn);
        }
        System.out.println(mn);
        Method stdinReadByte = IOImpl.class.getMethod("stdinReadByte");
        Method stdoutWriteByteDesc = IOImpl.class.getMethod("stdoutWriteByte", int.class);
        Object aoc = adapt("aoc", s -> new ModuleAdapter(s) {
            @Override
            public void visitEnd() {
                super.visitEnd();
                FieldNode ioImplFn = new FieldNode(Opcodes.ACC_PUBLIC, "ioImpl", Type.getDescriptor(IOImpl.class), null, null);
                cn.fields.add(ioImplFn);
                for (MethodNode method : cn.methods) {
                    switch (method.name) {
                        case "stdin_read_byte":
                            method.instructions.clear();
                            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, cn.name, ioImplFn.name, ioImplFn.desc));
                            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                                    Type.getInternalName(IOImpl.class),
                                    stdinReadByte.getName(),
                                    Type.getMethodDescriptor(stdinReadByte)));
                            method.instructions.add(new InsnNode(Opcodes.IRETURN));
                            method.maxStack = 1;
                            break;
                        case "stdout_write_byte":
                            method.instructions.clear();
                            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, cn.name, ioImplFn.name, ioImplFn.desc));
                            method.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
                            method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
                                    Type.getInternalName(IOImpl.class),
                                    stdoutWriteByteDesc.getName(),
                                    Type.getMethodDescriptor(stdoutWriteByteDesc)));
                            method.instructions.add(new InsnNode(Opcodes.RETURN));
                            method.maxStack = 2;
                            break;
                    }
                }
            }
        });
        m = new Matcher(aoc);
    }

    String getInput(String file) throws IOException {
        try (Reader rd = new InputStreamReader(
                Objects.requireNonNull(AocTest.class.getResourceAsStream("/input/" + file)),
                StandardCharsets.UTF_8
        )) {
            int bufferSize = 1024;
            char[] buffer = new char[bufferSize];
            StringBuilder out = new StringBuilder();
            for (int numRead; (numRead = rd.read(buffer, 0, buffer.length)) > 0; ) {
                out.append(buffer, 0, numRead);
            }
            return out.toString();
        }
    }

    @Test
    void day00() throws Throwable {
        m.assertMatches(
                "day_00",
                "Hello, world!",
                "Hello, world!"
        );
    }

    @Test
    void day01() throws Throwable {
        m.assertMatches(
                "day_01",
                getInput("1.txt"),
                "Fuel: 3126794\n" +
                        "Recursively: 4687331"
        );
    }
}

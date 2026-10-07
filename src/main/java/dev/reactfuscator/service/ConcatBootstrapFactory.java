package dev.reactfuscator.service;

import dev.reactfuscator.runtime.ConcatBootstrapTemplate;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;

public final class ConcatBootstrapFactory {
    public MethodNode create(String owner, String name, String decoder) {
        String resource =
                "/" + ConcatBootstrapTemplate.class.getName().replace('.', '/') + ".class";
        try (InputStream input = ConcatBootstrapTemplate.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing concat bootstrap template");
            }
            ClassNode template = new ClassNode(Opcodes.ASM9);
            new ClassReader(input)
                    .accept(template, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            MethodNode method =
                    template.methods.stream()
                            .filter(m -> m.name.equals("link"))
                            .findFirst()
                            .orElseThrow();
            method.name = name;
            method.access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_VARARGS;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call
                        && call.owner.equals("dev/reactfuscator/runtime/StringCipherTemplate")) {
                    call.owner = owner;
                    call.name = decoder;
                }
            }
            return method;
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read concat bootstrap template", failure);
        }
    }
}

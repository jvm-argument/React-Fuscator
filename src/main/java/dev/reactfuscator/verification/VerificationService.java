package dev.reactfuscator.verification;

import dev.reactfuscator.analysis.HierarchyService;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.util.CheckClassAdapter;

public final class VerificationService {
    public byte[] encode(ClassNode node, HierarchyService hierarchy) {
        HierarchyClassWriter writer = new HierarchyClassWriter(hierarchy);
        node.accept(writer);
        return writer.toByteArray();
    }

    public void verify(byte[] bytes, HierarchyService hierarchy) {
        ClassReader reader = new ClassReader(bytes);
        reader.accept(new CheckClassAdapter(new ClassWriter(0), false), 0);
        ClassNode node = new ClassNode(Opcodes.ASM9);
        reader.accept(node, ClassReader.EXPAND_FRAMES);
        for (MethodNode method : node.methods) {
            if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0) {
                try {
                    new Analyzer<>(new HierarchyVerifier(node, hierarchy))
                            .analyze(node.name, method);
                } catch (AnalyzerException | RuntimeException e) {
                    throw new IllegalStateException(
                            "ASM verification failed: "
                                    + node.name
                                    + "#"
                                    + method.name
                                    + method.desc
                                    + ": "
                                    + e.getMessage(),
                            e);
                }
            }
        }
    }
}

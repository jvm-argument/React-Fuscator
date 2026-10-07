package dev.reactfuscator.io;

import dev.reactfuscator.model.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class JarReader {
    private final long maxExpandedBytes;
    public JarReader(long maxExpandedBytes) { this.maxExpandedBytes = maxExpandedBytes; }
    public ArchiveModel read(Path path) throws IOException {
        ArchiveModel archive = new ArchiveModel();
        Set<String> seen = new HashSet<>();
        long expanded = 0;
        try (ZipFile zip = new ZipFile(path.toFile())) {
            for (ZipEntry entry : Collections.list(zip.entries())) {
                String name = entry.getName();
                if (!seen.add(name)) throw new IOException("Duplicate JAR entry: " + name);
                if (entry.isDirectory()) continue;
                if (name.startsWith("/") || name.contains("\\") || Arrays.asList(name.split("/")).contains("..")) throw new IOException("Invalid JAR entry: " + name);
                if (entry.getSize() > maxExpandedBytes || entry.getSize() < 0) throw new IOException("Oversized or unknown-size JAR entry: " + name);
                byte[] bytes;
                try (InputStream in = zip.getInputStream(entry)) { bytes = in.readNBytes((int)Math.min(maxExpandedBytes - expanded + 1, Integer.MAX_VALUE)); }
                expanded += bytes.length;
                if (expanded > maxExpandedBytes) throw new IOException("JAR exceeds expanded size limit " + maxExpandedBytes);
                if (name.endsWith(".class") && !name.startsWith("META-INF/versions/")) {
                    ClassNode node = new ClassNode(Opcodes.ASM9);
                    try { new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES); }
                    catch (RuntimeException e) { throw new IOException("Cannot parse class " + name, e); }
                    if (!name.equals(node.name + ".class")) throw new IOException("Class path/name mismatch: " + name + " / " + node.name);
                    archive.classes().put(node.name, new ClassModel(name, bytes, node));
                } else archive.resources().put(name, bytes);
            }
        }
        if (archive.classes().isEmpty()) throw new IOException("No root classes found in " + path);
        return archive;
    }
}

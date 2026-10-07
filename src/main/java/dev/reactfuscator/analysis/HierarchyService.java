package dev.reactfuscator.analysis;

import dev.reactfuscator.model.ArchiveModel;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public final class HierarchyService {
    private final Map<String, ClassInfo> input = new HashMap<>();
    private final Map<String, byte[]> libraries = new HashMap<>();
    private final Map<String, ClassInfo> cache = new HashMap<>();
    private final Set<String> missing = new TreeSet<>();
    private final boolean strict;

    public HierarchyService(ArchiveModel archive, List<Path> paths, boolean strict)
            throws IOException {
        this.strict = strict;
        refresh(archive);
        for (Path path : paths) {
            if (!Files.exists(path)) {
                throw new IOException("Library does not exist: " + path);
            }
            if (Files.isDirectory(path)) {
                try (var files = Files.walk(path)) {
                    for (Path p : files.filter(Files::isRegularFile).sorted().toList()) {
                        if (p.toString().endsWith(".jar")) {
                            index(p);
                        } else if (p.toString().endsWith(".class")) {
                            addLibrary(Files.readAllBytes(p));
                        }
                    }
                }
            } else {
                index(path);
            }
        }
    }

    private void index(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            Map<String, Integer> versions = new HashMap<>();
            boolean multiRelease = false;
            ZipEntry manifest = zip.getEntry("META-INF/MANIFEST.MF");
            if (manifest != null) {
                try (InputStream in = zip.getInputStream(manifest)) {
                    multiRelease =
                            "true"
                                    .equalsIgnoreCase(
                                            new java.util.jar.Manifest(in)
                                                    .getMainAttributes()
                                                    .getValue("Multi-Release"));
                }
            }
            for (ZipEntry e : Collections.list(zip.entries())) {
                if (e.getName().endsWith(".class") && !e.isDirectory()) {
                    String name = e.getName();
                    int version = 0;
                    if (name.startsWith("META-INF/versions/")) {
                        if (!multiRelease) {
                            continue;
                        }
                        String[] parts = name.split("/", 4);
                        try {
                            version = Integer.parseInt(parts[2]);
                        } catch (NumberFormatException ex) {
                            continue;
                        }
                        if (version > Runtime.version().feature()) {
                            continue;
                        }
                        name = parts[3];
                    }
                    if (versions.getOrDefault(name, -1) > version) {
                        continue;
                    }
                    versions.put(name, version);
                    try (InputStream in = zip.getInputStream(e)) {
                        addLibrary(in.readAllBytes());
                    }
                }
            }
            for (ZipEntry e : Collections.list(zip.entries())) {
                if (e.getName().endsWith(".jar") && !e.isDirectory()) {
                    try (InputStream in = zip.getInputStream(e)) {
                        indexNested(in, 1);
                    }
                }
            }
        }
    }

    private void indexNested(InputStream input, int depth) throws IOException {
        if (depth > 8) {
            throw new IOException("Nested library JAR depth exceeds 8");
        }
        try (ZipInputStream zip = new ZipInputStream(input)) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                if (e.isDirectory()
                        || (!e.getName().endsWith(".class") && !e.getName().endsWith(".jar"))) {
                    continue;
                }
                byte[] bytes = zip.readNBytes(64 * 1024 * 1024 + 1);
                if (bytes.length > 64 * 1024 * 1024) {
                    throw new IOException("Nested library entry too large: " + e.getName());
                }
                if (e.getName().endsWith(".class")
                        && !e.getName().startsWith("META-INF/versions/")) {
                    addLibrary(bytes);
                } else if (e.getName().endsWith(".jar")) {
                    indexNested(new ByteArrayInputStream(bytes), depth + 1);
                }
            }
        }
    }

    private void addLibrary(byte[] bytes) {
        libraries.put(new ClassReader(bytes).getClassName(), bytes);
    }

    public void refresh(ArchiveModel archive) {
        input.clear();
        archive.classes().values().forEach(c -> input.put(c.node().name, new ClassInfo(c.node())));
    }

    public void update(ClassNode node) {
        input.put(node.name, new ClassInfo(node));
    }

    public ClassInfo resolve(String name) {
        ClassInfo info = input.get(name);
        if (info != null) {
            return info;
        }
        info = cache.get(name);
        if (info != null) {
            return info;
        }
        byte[] bytes = libraries.get(name);
        if (bytes == null) {
            try (InputStream in =
                    ClassLoader.getPlatformClassLoader().getResourceAsStream(name + ".class")) {
                if (in != null) {
                    bytes = in.readAllBytes();
                }
            } catch (IOException e) {
                throw new IllegalStateException("Cannot read JDK class " + name, e);
            }
        }
        if (bytes == null) {
            missing.add(name);
            if (strict) {
                throw new IllegalStateException(
                        "Missing dependency: "
                                + name.replace('/', '.')
                                + "; supply --library JAR or directory");
            }
            return new ClassInfo(
                    name,
                    "java/lang/Object".equals(name) ? null : "java/lang/Object",
                    List.of(),
                    0);
        }
        ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(bytes)
                .accept(
                        node,
                        ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        info = new ClassInfo(node);
        cache.put(name, info);
        return info;
    }

    public boolean isInterface(String name) {
        return (resolve(name).access() & Opcodes.ACC_INTERFACE) != 0;
    }

    public boolean assignable(String target, String source) {
        if (target.equals(source) || target.equals("java/lang/Object")) {
            return true;
        }
        if (source.startsWith("[")) {
            if (target.equals("java/lang/Cloneable") || target.equals("java/io/Serializable")) {
                return true;
            }
            if (!target.startsWith("[")) {
                return false;
            }
            String t = component(target), s = component(source);
            if (primitiveComponent(target) || primitiveComponent(source)) {
                return primitiveComponent(target) && primitiveComponent(source) && t.equals(s);
            }
            return assignable(t, s);
        }
        if (target.startsWith("[")) {
            return false;
        }
        Deque<String> pending = new ArrayDeque<>();
        pending.add(source);
        Set<String> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            String next = pending.remove();
            if (!visited.add(next)) {
                continue;
            }
            if (target.equals(next)) {
                return true;
            }
            ClassInfo info = resolve(next);
            if (info.parent() != null) {
                pending.add(info.parent());
            }
            pending.addAll(info.interfaces());
        }
        return false;
    }

    public String commonSuper(String a, String b) {
        if (assignable(a, b)) {
            return a;
        }
        if (assignable(b, a)) {
            return b;
        }
        if (a.startsWith("[") && b.startsWith("[")) {
            String ac = component(a), bc = component(b);
            if (!primitiveComponent(a) && !primitiveComponent(b)) {
                String merged = commonSuper(ac, bc);
                return "[" + (merged.startsWith("[") ? merged : "L" + merged + ";");
            }
            return "java/lang/Object";
        }
        if (a.startsWith("[") || b.startsWith("[") || isInterface(a) || isInterface(b)) {
            return "java/lang/Object";
        }
        Set<String> visited = new HashSet<>();
        for (String cursor = resolve(a).parent();
                cursor != null;
                cursor = resolve(cursor).parent()) {
            if (!visited.add(cursor)) {
                throw new IllegalStateException("Circular hierarchy: " + a);
            }
            if (assignable(cursor, b)) {
                return cursor;
            }
        }
        return "java/lang/Object";
    }

    private String component(String array) {
        String value = array.substring(1);
        return value.startsWith("L") ? value.substring(1, value.length() - 1) : value;
    }

    private boolean primitiveComponent(String array) {
        return array.length() == 2 && "ZBCSIJFD".indexOf(array.charAt(1)) >= 0;
    }

    public Set<String> missing() {
        return Set.copyOf(missing);
    }

    public Collection<byte[]> libraryBytes() {
        return libraries.values();
    }

    public void validateParents() {
        for (ClassInfo info : input.values()) {
            Set<String> visited = new HashSet<>();
            for (String p = info.parent(); p != null; p = resolve(p).parent()) {
                if (!visited.add(p)) {
                    throw new IllegalStateException("Circular hierarchy: " + info.name());
                }
            }
            for (String itf : info.interfaces()) {
                resolve(itf);
            }
        }
    }
}

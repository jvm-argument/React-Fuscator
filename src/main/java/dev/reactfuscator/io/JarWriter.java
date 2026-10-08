package dev.reactfuscator.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class JarWriter {
    private final AtomicFileWriter files;

    public JarWriter(AtomicFileWriter files) {
        this.files = files;
    }

    public void write(Path destination, Map<String, byte[]> entries) throws IOException {
        Path absolute = destination.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temp = Files.createTempFile(absolute.getParent(), ".react-fuscator-", ".jar");
        try {
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temp))) {
                for (var e : new TreeMap<>(entries).entrySet()) {
                    ZipEntry entry = new ZipEntry(e.getKey());
                    entry.setTime(0);
                    if (e.getKey().endsWith(".jar")) {
                        CRC32 crc = new CRC32();
                        crc.update(e.getValue());
                        entry.setMethod(ZipEntry.STORED);
                        entry.setSize(e.getValue().length);
                        entry.setCompressedSize(e.getValue().length);
                        entry.setCrc(crc.getValue());
                    }
                    out.putNextEntry(entry);
                    out.write(e.getValue());
                    out.closeEntry();
                }
            }
            files.publish(temp, absolute);
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}

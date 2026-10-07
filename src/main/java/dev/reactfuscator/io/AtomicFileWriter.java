package dev.reactfuscator.io;

import java.io.*;
import java.nio.file.*;

public final class AtomicFileWriter {
    public void write(Path destination, byte[] bytes) throws IOException {
        Path absolute = destination.toAbsolutePath();
        Files.createDirectories(absolute.getParent());
        Path temp = Files.createTempFile(absolute.getParent(), ".react-fuscator-", ".tmp");
        try { Files.write(temp, bytes); publish(temp, absolute); }
        finally { Files.deleteIfExists(temp); }
    }
    public void publish(Path temp, Path destination) throws IOException {
        try { Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING); }
    }
}

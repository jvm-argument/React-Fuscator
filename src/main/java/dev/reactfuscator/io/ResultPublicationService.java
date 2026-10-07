package dev.reactfuscator.io;

import com.google.gson.GsonBuilder;

import dev.reactfuscator.mapping.*;
import dev.reactfuscator.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class ResultPublicationService {
    private final JarWriter jars;
    private final MappingWriter mappings;
    private final AtomicFileWriter files;

    public ResultPublicationService(
            JarWriter jars, MappingWriter mappings, AtomicFileWriter files) {
        this.jars = jars;
        this.mappings = mappings;
        this.files = files;
    }

    public ObfuscationResult publish(
            Path output,
            Map<String, byte[]> entries,
            MappingModel mapping,
            RunStatistics stats,
            long started)
            throws IOException {
        Path directory = output.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path stage = Files.createTempDirectory(directory, ".react-fuscator-result-");
        Path mappingPath = Path.of(output + ".mapping.json"),
                reportPath = Path.of(output + ".report.json");
        Map<Path, byte[]> previous = new LinkedHashMap<>();
        Set<Path> published = new LinkedHashSet<>();
        try {
            Path stagedJar = stage.resolve("output.jar"),
                    stagedMapping = stage.resolve("mapping.json"),
                    stagedReport = stage.resolve("report.json");
            jars.write(stagedJar, entries);
            mappings.write(stagedMapping, mapping, stats.seed);
            stats.outputBytes = Files.size(stagedJar);
            stats.elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            files.write(
                    stagedReport,
                    new GsonBuilder()
                            .setPrettyPrinting()
                            .disableHtmlEscaping()
                            .create()
                            .toJson(stats)
                            .getBytes(StandardCharsets.UTF_8));
            for (Path path : List.of(mappingPath, reportPath)) {
                if (Files.exists(path) && !Files.isRegularFile(path)) {
                    throw new IOException("Artifact destination is not a regular file: " + path);
                }
                previous.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);
            }
            files.publish(stagedMapping, mappingPath);
            published.add(mappingPath);
            files.publish(stagedReport, reportPath);
            published.add(reportPath);
            files.publish(stagedJar, output);
            return new ObfuscationResult(output, mappingPath, reportPath, stats);
        } catch (IOException failure) {
            for (Path path : published) {
                try {
                    byte[] old = previous.get(path);
                    if (old == null) {
                        Files.deleteIfExists(path);
                    } else {
                        files.write(path, old);
                    }
                } catch (IOException restore) {
                    failure.addSuppressed(restore);
                }
            }
            throw failure;
        } finally {
            try (var children = Files.list(stage)) {
                for (Path path : children.toList()) {
                    Files.deleteIfExists(path);
                }
            }
            Files.deleteIfExists(stage);
        }
    }
}

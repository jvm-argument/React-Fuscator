package dev.reactfuscator.testing;

import dev.reactfuscator.config.ObfuscationConfig;
import dev.reactfuscator.core.ApplicationFactory;
import dev.reactfuscator.io.AtomicFileWriter;
import dev.reactfuscator.io.JarWriter;
import dev.reactfuscator.model.ObfuscationResult;
import dev.reactfuscator.service.CancellationToken;
import dev.reactfuscator.service.ProgressListener;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.tools.ToolProvider;

public final class TestSupport {
    private final Path directory;

    public TestSupport(Path directory) {
        this.directory = directory;
    }

    public Path compile(
            Map<String, String> sources, int release, Map<String, byte[]> resources, String main)
            throws Exception {
        Path source = directory.resolve("source-" + UUID.randomUUID()),
                classes = directory.resolve("classes-" + UUID.randomUUID());
        Files.createDirectories(source);
        Files.createDirectories(classes);
        List<String> arguments =
                new ArrayList<>(
                        List.of(
                                "--release",
                                Integer.toString(release),
                                "-g",
                                "-parameters",
                                "-encoding",
                                "UTF-8",
                                "-d",
                                classes.toString()));
        for (var entry : sources.entrySet()) {
            Path path = source.resolve(entry.getKey().replace('.', '/') + ".java");
            Files.createDirectories(path.getParent());
            Files.writeString(path, entry.getValue(), StandardCharsets.UTF_8);
            arguments.add(path.toString());
        }
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int status =
                ToolProvider.getSystemJavaCompiler()
                        .run(null, null, errors, arguments.toArray(String[]::new));
        if (status != 0) {
            throw new IllegalStateException(errors.toString(StandardCharsets.UTF_8));
        }
        Map<String, byte[]> entries = new LinkedHashMap<>(resources);
        try (var files = Files.walk(classes)) {
            for (Path path : files.filter(Files::isRegularFile).toList()) {
                entries.put(
                        classes.relativize(path).toString().replace('\\', '/'),
                        Files.readAllBytes(path));
            }
        }
        if (main != null) {
            entries.put(
                    "META-INF/MANIFEST.MF",
                    ("Manifest-Version: 1.0\r\nMain-Class: " + main + "\r\n\r\n")
                            .getBytes(StandardCharsets.UTF_8));
        }
        Path jar = directory.resolve("fixture-" + UUID.randomUUID() + ".jar");
        new JarWriter(new AtomicFileWriter()).write(jar, entries);
        return jar;
    }

    public ObfuscationResult protect(Path input, ObfuscationConfig config) throws Exception {
        ApplicationFactory factory = new ApplicationFactory();
        return factory.manager(factory.registry())
                .obfuscate(
                        input,
                        directory.resolve("protected-" + UUID.randomUUID() + ".jar"),
                        config,
                        new ProgressListener() {
                            public void progress(double fraction, String message) {
                            }

                            public void log(String message) {
                            }
                        },
                        new CancellationToken());
    }

    public String execute(Path jar) throws Exception {
        Path log = directory.resolve("run-" + UUID.randomUUID() + ".log");
        Path executable =
                Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java");
        Process process =
                new ProcessBuilder(
                                executable.toString(),
                                "-Dstdout.encoding=UTF-8",
                                "-Dstderr.encoding=UTF-8",
                                "-Xverify:all",
                                "-jar",
                                jar.toString())
                        .redirectErrorStream(true)
                        .redirectOutput(log.toFile())
                        .start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("Fixture timed out");
        }
        String result = Files.readString(log);
        if (process.exitValue() != 0) {
            throw new AssertionError("Fixture failed (" + process.exitValue() + "): " + result);
        }
        return result;
    }
}

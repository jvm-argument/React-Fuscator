package dev.reactfuscator.cli;

import dev.reactfuscator.analysis.*;
import dev.reactfuscator.io.JarReader;
import dev.reactfuscator.model.ArchiveModel;

import picocli.CommandLine.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

@Command(
        name = "inspect",
        description = "Inspect bytecode versions, platform metadata and missing dependencies.",
        mixinStandardHelpOptions = true)
public final class InspectCommand implements Callable<Integer> {
    @Parameters(index = "0")
    private Path input;

    @Option(names = {"-l", "--library"})
    private List<Path> libraries = new ArrayList<>();

    @Spec private Model.CommandSpec spec;

    public Integer call() throws Exception {
        ArchiveModel archive = new JarReader(1024L * 1024 * 1024).read(input);
        var out = spec.commandLine().getOut();
        out.println(
                "Classes: "
                        + archive.classes().size()
                        + ", resources: "
                        + archive.resources().size());
        var versions =
                archive.classes().values().stream()
                        .map(c -> (c.node().version & 0xffff) - 44)
                        .distinct()
                        .sorted()
                        .toList();
        out.println("Java bytecode: " + versions);
        for (String key : List.of("plugin.yml", "paper-plugin.yml", "fabric.mod.json")) {
            if (archive.resources().containsKey(key)) {
                out.println(
                        key
                                + "\n"
                                + new String(archive.resources().get(key), StandardCharsets.UTF_8));
            }
        }
        HierarchyService hierarchy = new HierarchyService(archive, libraries, false);
        new DependencyAnalyzer().references(archive).forEach(hierarchy::resolve);
        out.println("Missing dependencies: " + hierarchy.missing().size());
        hierarchy.missing().stream().sorted().forEach(out::println);
        out.flush();
        return 0;
    }
}

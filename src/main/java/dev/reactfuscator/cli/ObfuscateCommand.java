package dev.reactfuscator.cli;

import dev.reactfuscator.config.ConfigParser;
import dev.reactfuscator.config.ObfuscationConfig;
import dev.reactfuscator.config.ProtectionProfile;
import dev.reactfuscator.config.TransformerSettings;
import dev.reactfuscator.core.ApplicationFactory;
import dev.reactfuscator.model.ObfuscationResult;
import dev.reactfuscator.registry.TransformerRegistry;
import dev.reactfuscator.service.CancellationToken;

import picocli.CommandLine.Command;
import picocli.CommandLine.Model;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "obfuscate",
        description = "Obfuscate a JAR with mandatory ASM verification.",
        mixinStandardHelpOptions = true)
public final class ObfuscateCommand implements Callable<Integer> {
    @Parameters(index = "0", description = "Input JAR")
    private Path input;

    @Option(
            names = {"-o", "--output"},
            required = true,
            description = "Output JAR (different from input)")
    private Path output;

    @Option(
            names = {"-c", "--config"},
            description = "JSON configuration")
    private Path configFile;

    @Option(
            names = {"-p", "--profile"},
            description = "LIGHT, NORMAL, STRONG, EXTREME")
    private ProtectionProfile profile;

    @Option(
            names = {"-l", "--library"},
            description = "Dependency JAR or directory; repeatable")
    private List<Path> libraries = new ArrayList<>();

    @Option(names = "--seed", description = "Reproducible run seed")
    private Long seed;

    @Option(names = "--include", description = "Internal-name class glob; repeatable")
    private List<String> include = new ArrayList<>();

    @Option(names = "--exclude", description = "Class or owner#method(descriptor) glob; repeatable")
    private List<String> exclude = new ArrayList<>();

    @Option(names = "--keep", description = "Keep class and member names; still transform code")
    private List<String> keep = new ArrayList<>();

    @Option(names = "--keep-member", description = "Keep owner#member glob")
    private List<String> keepMembers = new ArrayList<>();

    @Option(names = "--disable", description = "Disable transformer ID; repeatable")
    private List<String> disable = new ArrayList<>();

    @Option(names = "--enable", description = "Enable transformer ID; repeatable")
    private List<String> enable = new ArrayList<>();

    @Option(
            names = "--set",
            description = "Transformer setting: id.density=0..100 or id.rounds=1..8")
    private List<String> settings = new ArrayList<>();

    @Option(names = "--no-rename", description = "Keep all class/member names")
    private boolean noRename;

    @Option(
            names = "--keep-public-api",
            description =
                    "Retain all public/protected members; external API callbacks are always"
                            + " protected")
    private boolean keepPublicApi;

    @Option(
            names = "--rename-serialization",
            description =
                    "Rename enum/record/Serializable class identities; existing serialized data may"
                            + " require migration")
    private boolean renameSerialization;

    @Option(names = "--keep-mixin-names", description = "Retain Mixin class/package identities")
    private boolean keepMixinNames;

    @Option(names = "--no-scatter", description = "Use one target package per original package")
    private boolean noScatter;

    @Option(
            names = "--allow-missing-dependencies",
            description = "Diagnostic mode: allow incomplete hierarchy; output is not certified")
    private boolean allowMissing;

    @Spec private Model.CommandSpec spec;

    public Integer call() throws Exception {
        ConfigParser parser = new ConfigParser();
        ObfuscationConfig config =
                configFile == null ? new ObfuscationConfig() : parser.read(configFile);
        if (profile != null) {
            config.profile = profile;
        }
        if (seed != null) {
            config.seed = seed;
        }
        libraries.forEach(p -> config.libraries.add(p.toAbsolutePath().toString()));
        if (!include.isEmpty()) {
            config.include = include;
        }
        config.exclude.addAll(exclude);
        config.keep.addAll(keep);
        config.keepMembers.addAll(keepMembers);
        for (String id : disable) {
            config.transformers.computeIfAbsent(id, x -> new TransformerSettings()).enabled = false;
        }
        for (String id : enable) {
            config.transformers.computeIfAbsent(id, x -> new TransformerSettings()).enabled = true;
        }
        for (String setting : settings) {
            String[] halves = setting.split("=", 2), key = halves[0].split("\\.", 2);
            if (halves.length != 2 || key.length != 2) {
                throw new IllegalArgumentException("Invalid setting: " + setting);
            }
            TransformerSettings value =
                    config.transformers.computeIfAbsent(key[0], x -> new TransformerSettings());
            int number = Integer.parseInt(halves[1]);
            switch (key[1]) {
                case "density" -> value.density = number;
                case "rounds" -> value.rounds = number;
                default -> throw new IllegalArgumentException("Unknown setting: " + setting);
            }
        }
        if (noRename) {
            config.renameClasses = false;
            config.renameMethods = false;
            config.renameFields = false;
        }
        if (keepPublicApi) {
            config.preservePublicApi = true;
        }
        if (renameSerialization) {
            config.preserveSerializationNames = false;
        }
        if (keepMixinNames) {
            config.renameMixins = false;
        }
        if (noScatter) {
            config.scatterPackages = false;
        }
        if (allowMissing) {
            config.strictDependencies = false;
        }
        ApplicationFactory factory = new ApplicationFactory();
        TransformerRegistry registry = factory.registry();
        CancellationToken cancellation = new CancellationToken();
        Thread hook = new Thread(cancellation::cancel, "react-fuscator-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
        try {
            ObfuscationResult result =
                    factory.manager(registry)
                            .obfuscate(
                                    input,
                                    output,
                                    config,
                                    new ConsoleProgressListener(spec.commandLine().getOut()),
                                    cancellation);
            spec.commandLine()
                    .getOut()
                    .println(
                            "Output: "
                                    + result.output()
                                    + "\nMapping: "
                                    + result.mapping()
                                    + "\nReport: "
                                    + result.report());
            return 0;
        } finally {
            Runtime.getRuntime().removeShutdownHook(hook);
        }
    }
}

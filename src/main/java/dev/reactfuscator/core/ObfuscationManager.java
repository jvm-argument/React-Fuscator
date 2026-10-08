package dev.reactfuscator.core;

import dev.reactfuscator.analysis.CompatibilityAnalyzer;
import dev.reactfuscator.analysis.HierarchyService;
import dev.reactfuscator.analysis.KeepPolicy;
import dev.reactfuscator.analysis.leak.LeakScannerService;
import dev.reactfuscator.analysis.leak.LeakSnapshotFactory;
import dev.reactfuscator.config.ConfigParser;
import dev.reactfuscator.config.ObfuscationConfig;
import dev.reactfuscator.io.JarReader;
import dev.reactfuscator.io.ResultPublicationService;
import dev.reactfuscator.mapping.MappingModel;
import dev.reactfuscator.mapping.MappingPlanner;
import dev.reactfuscator.model.ArchiveModel;
import dev.reactfuscator.model.ClassModel;
import dev.reactfuscator.model.LeakSnapshot;
import dev.reactfuscator.model.ObfuscationResult;
import dev.reactfuscator.model.RunStatistics;
import dev.reactfuscator.platform.PlatformHandler;
import dev.reactfuscator.registry.PlatformRegistry;
import dev.reactfuscator.registry.TransformerRegistry;
import dev.reactfuscator.remap.BytecodeRemapService;
import dev.reactfuscator.remap.ResourceRemapService;
import dev.reactfuscator.service.CancellationToken;
import dev.reactfuscator.service.ProgressListener;
import dev.reactfuscator.transform.TransformerPipeline;
import dev.reactfuscator.util.BytecodeHelper;
import dev.reactfuscator.util.NameFactory;
import dev.reactfuscator.verification.VerificationService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

public final class ObfuscationManager {
    private final ConfigParser configs;
    private final JarReader reader;
    private final ResultPublicationService publication;
    private final PlatformRegistry platforms;
    private final CompatibilityAnalyzer compatibility;
    private final MappingPlanner planner;
    private final BytecodeRemapService bytecodeRemap;
    private final ResourceRemapService resourceRemap;
    private final TransformerRegistry registry;
    private final TransformerPipeline pipeline;
    private final VerificationService verification;
    private final LeakSnapshotFactory leakSnapshots;
    private final LeakScannerService leakScanner;

    public ObfuscationManager(
            ConfigParser configs,
            JarReader reader,
            ResultPublicationService publication,
            PlatformRegistry platforms,
            CompatibilityAnalyzer compatibility,
            MappingPlanner planner,
            BytecodeRemapService bytecodeRemap,
            ResourceRemapService resourceRemap,
            TransformerRegistry registry,
            TransformerPipeline pipeline,
            VerificationService verification,
            LeakSnapshotFactory leakSnapshots,
            LeakScannerService leakScanner) {
        this.configs = configs;
        this.reader = reader;
        this.publication = publication;
        this.platforms = platforms;
        this.compatibility = compatibility;
        this.planner = planner;
        this.bytecodeRemap = bytecodeRemap;
        this.resourceRemap = resourceRemap;
        this.registry = registry;
        this.pipeline = pipeline;
        this.verification = verification;
        this.leakSnapshots = leakSnapshots;
        this.leakScanner = leakScanner;
    }

    public ObfuscationResult obfuscate(
            Path input,
            Path output,
            ObfuscationConfig requested,
            ProgressListener listener,
            CancellationToken cancellation)
            throws IOException {
        long started = System.nanoTime();
        ObfuscationConfig config = configs.copy(requested);
        configs.validate(config);
        for (String id : config.transformers.keySet()) {
            if (!registry.ids().contains(id)) {
                throw new IllegalArgumentException("Unknown transformer: " + id);
            }
        }
        for (var transformer : registry.ordered()) {
            if (config.transformers.containsKey(transformer.descriptor().id())) {
                var descriptor = transformer.descriptor();
                var settings = config.settings(descriptor.id());
                if (settings.density >= 0 && !descriptor.densityConfigurable()) {
                    throw new IllegalArgumentException(
                            descriptor.id() + " does not have a density setting");
                }
                if (settings.rounds >= 1 && !descriptor.roundsConfigurable()) {
                    throw new IllegalArgumentException(
                            descriptor.id() + " does not have a rounds setting");
                }
            }
        }
        input = input.toAbsolutePath().normalize();
        output = output.toAbsolutePath().normalize();
        if (input.equals(output) || (Files.exists(output) && Files.isSameFile(input, output))) {
            throw new IllegalArgumentException("Input and output must be different files");
        }
        RunStatistics runStatistics = new RunStatistics();
        runStatistics.profile = config.profile.name();
        runStatistics.inputBytes = Files.size(input);
        runStatistics.seed = config.seed == null ? new SecureRandom().nextLong() : config.seed;
        listener.progress(0, "Reading JAR");
        listener.log("React-Fuscator / " + config.profile + " / seed " + runStatistics.seed);
        cancellation.check();
        ArchiveModel archive = reader.read(input);
        runStatistics.classes = archive.classes().size();
        LeakSnapshot leakSnapshot = leakSnapshots.capture(archive, config);
        KeepPolicy keeps = new KeepPolicy(config);
        List<PlatformHandler> detected = platforms.detect(archive);
        for (PlatformHandler platform : detected) {
            listener.log("Platform: " + platform.id());
            platform.analyze(archive, keeps);
        }
        HierarchyService hierarchy =
                new HierarchyService(archive, config.libraryPaths(), config.strictDependencies);
        hierarchy.validateParents();
        compatibility.analyze(archive, keeps, hierarchy, runStatistics);
        MappingModel mapping =
                planner.plan(
                        archive,
                        config,
                        keeps,
                        hierarchy,
                        new NameFactory(
                                new SplittableRandom(runStatistics.seed ^ 0x4d415050494e47L)));
        runStatistics.renamedClasses =
                (int)
                        mapping.classes().entrySet().stream()
                                .filter(e -> !e.getKey().equals(e.getValue()))
                                .count();
        runStatistics.renamedMethods = mapping.methods().size();
        runStatistics.renamedFields = mapping.fields().size();
        runStatistics.keptClasses.putAll(keeps.reasons());
        NameFactory generatedNames =
                new NameFactory(new SplittableRandom(runStatistics.seed ^ 0x5452414e53464f52L));
        generatedNames.reserve(mapping.methods().values());
        generatedNames.reserve(mapping.fields().values());
        ObfuscationContext context =
                new ObfuscationContext(
                        archive,
                        config,
                        keeps,
                        hierarchy,
                        runStatistics,
                        listener,
                        cancellation,
                        new SplittableRandom(runStatistics.seed),
                        generatedNames,
                        new BytecodeHelper());
        listener.progress(.15, "Transforming bytecode");
        pipeline.execute(context);
        cancellation.check();
        listener.progress(.82, "Remapping symbols and resources");
        for (PlatformHandler platform : detected) {
            platform.remap(archive, mapping);
        }
        resourceRemap.remap(archive, mapping, config, runStatistics);
        bytecodeRemap.remap(archive, mapping);
        hierarchy.refresh(archive);
        Map<String, byte[]> entries = new LinkedHashMap<>(archive.resources());
        int done = 0;
        runStatistics.outputClasses = archive.classes().size();
        for (ClassModel model : archive.classes().values()) {
            cancellation.check();
            byte[] bytes = verification.encode(model.node(), hierarchy);
            verification.verify(bytes, hierarchy);
            if (entries.put(model.node().name + ".class", bytes) != null) {
                throw new IOException("Output class collision: " + model.node().name);
            }
            listener.progress(
                    .85 + .10 * ++done / archive.classes().size(),
                    "Verifying " + done + " / " + archive.classes().size());
        }
        listener.progress(.97, "Leak Scanner");
        listener.log("Stage: Leak Scanner");
        runStatistics.protection =
                leakScanner.scan(leakSnapshot, mapping, entries, keeps, runStatistics);
        listener.log(
                "Protection: "
                        + runStatistics.protection.encryptedStrings
                        + " encrypted strings, "
                        + runStatistics.protection.transformedMethods
                        + " transformed methods, "
                        + runStatistics.protection.removedDebugAttributes
                        + " debug attributes removed; metadata leaks "
                        + runStatistics.protection.metadataLeaksFound
                        + " → "
                        + runStatistics.protection.metadataLeaksRemaining);
        if (runStatistics.protection.findingsByStatus.getOrDefault("UNRESOLVED", 0L) > 0) {
            runStatistics.warnings.add(
                    "Leak Scanner: "
                            + runStatistics.protection.findingsByStatus.get("UNRESOLVED")
                            + " unresolved findings; details and retained-contract/ambiguous tokens"
                            + " are in the protection report.");
        }
        if (!hierarchy.missing().isEmpty()) {
            if (config.strictDependencies) {
                throw new IOException(
                        "Missing dependencies discovered during analysis: "
                                + hierarchy.missing()
                                + "; supply --library JAR or directory");
            }
            runStatistics.warnings.add(
                    "INCOMPLETE CLASSPATH: verification used fallback hierarchy for "
                            + hierarchy.missing()
                            + ". This output is not certified for deployment.");
        }
        cancellation.check();
        for (Path path :
                List.of(Path.of(output + ".mapping.json"), Path.of(output + ".report.json"))) {
            if (input.equals(path) || (Files.exists(path) && Files.isSameFile(input, path))) {
                throw new IllegalArgumentException("Sidecar destination overlaps input");
            }
        }
        ObfuscationResult result =
                publication.publish(output, entries, mapping, runStatistics, started);
        runStatistics.warnings.forEach(listener::log);
        listener.progress(1, "Complete");
        listener.log(
                "Verified "
                        + runStatistics.outputClasses
                        + " classes ("
                        + runStatistics.generatedClasses
                        + " generated); renamed "
                        + runStatistics.renamedClasses
                        + " classes, "
                        + runStatistics.renamedMethods
                        + " methods, "
                        + runStatistics.renamedFields
                        + " fields");
        return result;
    }
}

package dev.reactfuscator.core;

import dev.reactfuscator.analysis.*;
import dev.reactfuscator.analysis.leak.*;
import dev.reactfuscator.config.ConfigParser;
import dev.reactfuscator.io.*;
import dev.reactfuscator.mapping.*;
import dev.reactfuscator.platform.fabric.FabricMetadataHandler;
import dev.reactfuscator.platform.paper.PaperMetadataHandler;
import dev.reactfuscator.registry.*;
import dev.reactfuscator.remap.*;
import dev.reactfuscator.service.*;
import dev.reactfuscator.transform.*;
import dev.reactfuscator.transform.impl.*;
import dev.reactfuscator.verification.VerificationService;

import java.util.List;

public final class ApplicationFactory {
    public TransformerRegistry registry() {
        FlowTemplateFactory flow = new FlowTemplateFactory();
        StringCipherService strings = new StringCipherService();
        return new TransformerRegistry(
                List.of(
                        new ClassNoiseTransformer(),
                        new ConstantFieldTransformer(),
                        new ConcatStringTransformer(strings, new ConcatBootstrapFactory()),
                        new DynamicStringTransformer(strings),
                        new StringEncryptionTransformer(strings),
                        new NumberObfuscationTransformer(flow),
                        new ControlFlowTransformer(),
                        new ControlFlowFlatteningTransformer(new ControlFlowGraphAnalyzer()),
                        new OpaquePredicateTransformer(flow),
                        new ExceptionFlowTransformer(new ExceptionSupportFactory()),
                        new JunkCodeTransformer(flow),
                        new IndirectionTransformer(
                                new BridgeMethodFactory(), new DispatcherMethodFactory()),
                        new ProxyMethodTransformer(),
                        new HelperProtectionTransformer(flow),
                        new DebugMetadataTransformer()));
    }

    public ObfuscationManager manager(TransformerRegistry registry) {
        AtomicFileWriter files = new AtomicFileWriter();
        VerificationService verification = new VerificationService();
        DebugAttributeInspector debug = new DebugAttributeInspector();
        return new ObfuscationManager(
                new ConfigParser(),
                new JarReader(1024L * 1024 * 1024),
                new ResultPublicationService(new JarWriter(files), new MappingWriter(files), files),
                new PlatformRegistry(
                        List.of(new PaperMetadataHandler(), new FabricMetadataHandler())),
                new CompatibilityAnalyzer(),
                new MappingPlanner(),
                new BytecodeRemapService(),
                new ResourceRemapService(),
                registry,
                new TransformerPipeline(registry, verification, new MethodFingerprintService()),
                verification,
                new LeakSnapshotFactory(debug),
                new LeakScannerService(new ConstantPoolReader(), debug));
    }
}

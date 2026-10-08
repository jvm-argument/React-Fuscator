package dev.reactfuscator.core;

import dev.reactfuscator.analysis.CompatibilityAnalyzer;
import dev.reactfuscator.analysis.ControlFlowGraphAnalyzer;
import dev.reactfuscator.analysis.leak.ConstantPoolReader;
import dev.reactfuscator.analysis.leak.DebugAttributeInspector;
import dev.reactfuscator.analysis.leak.LeakScannerService;
import dev.reactfuscator.analysis.leak.LeakSnapshotFactory;
import dev.reactfuscator.analysis.leak.LeakSymbolService;
import dev.reactfuscator.config.ConfigParser;
import dev.reactfuscator.io.AtomicFileWriter;
import dev.reactfuscator.io.JarReader;
import dev.reactfuscator.io.JarWriter;
import dev.reactfuscator.io.ResultPublicationService;
import dev.reactfuscator.mapping.MappingPlanner;
import dev.reactfuscator.mapping.MappingWriter;
import dev.reactfuscator.platform.fabric.FabricMetadataHandler;
import dev.reactfuscator.platform.paper.PaperMetadataHandler;
import dev.reactfuscator.registry.PlatformRegistry;
import dev.reactfuscator.registry.TransformerRegistry;
import dev.reactfuscator.remap.BytecodeRemapService;
import dev.reactfuscator.remap.ResourceRemapService;
import dev.reactfuscator.service.BridgeMethodFactory;
import dev.reactfuscator.service.ConcatBootstrapFactory;
import dev.reactfuscator.service.DispatcherMethodFactory;
import dev.reactfuscator.service.ExceptionSupportFactory;
import dev.reactfuscator.service.FlowTemplateFactory;
import dev.reactfuscator.service.MethodFingerprintService;
import dev.reactfuscator.service.StringCipherService;
import dev.reactfuscator.transform.TransformerPipeline;
import dev.reactfuscator.transform.impl.ClassNoiseTransformer;
import dev.reactfuscator.transform.impl.ConcatStringTransformer;
import dev.reactfuscator.transform.impl.ConstantFieldTransformer;
import dev.reactfuscator.transform.impl.ControlFlowFlatteningTransformer;
import dev.reactfuscator.transform.impl.ControlFlowTransformer;
import dev.reactfuscator.transform.impl.DebugMetadataTransformer;
import dev.reactfuscator.transform.impl.DynamicStringTransformer;
import dev.reactfuscator.transform.impl.ExceptionFlowTransformer;
import dev.reactfuscator.transform.impl.HelperProtectionTransformer;
import dev.reactfuscator.transform.impl.IndirectionTransformer;
import dev.reactfuscator.transform.impl.JunkCodeTransformer;
import dev.reactfuscator.transform.impl.NumberObfuscationTransformer;
import dev.reactfuscator.transform.impl.OpaquePredicateTransformer;
import dev.reactfuscator.transform.impl.ProxyMethodTransformer;
import dev.reactfuscator.transform.impl.StringEncryptionTransformer;
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
                new LeakScannerService(new ConstantPoolReader(), debug, new LeakSymbolService()));
    }
}

package dev.reactfuscator.cli;

import dev.reactfuscator.analysis.HierarchyService;
import dev.reactfuscator.io.JarReader;
import dev.reactfuscator.model.*;
import dev.reactfuscator.verification.VerificationService;
import picocli.CommandLine.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

@Command(name="verify",description="Verify every root class using ASM without executing application code.",mixinStandardHelpOptions=true)
public final class VerifyCommand implements Callable<Integer> {
    @Parameters(index="0") private Path input;
    @Option(names={"-l","--library"}) private List<Path> libraries=new ArrayList<>();
    @Spec private Model.CommandSpec spec;
    public Integer call() throws Exception {
        ArchiveModel archive=new JarReader(1024L*1024*1024).read(input);HierarchyService hierarchy=new HierarchyService(archive,libraries,true);hierarchy.validateParents();
        VerificationService verification=new VerificationService();for(ClassModel model:archive.classes().values()) verification.verify(model.originalBytes(),hierarchy);
        spec.commandLine().getOut().println("ASM verified "+archive.classes().size()+" classes");return 0;
    }
}

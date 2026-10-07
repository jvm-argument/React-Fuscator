package dev.reactfuscator.analysis;

import dev.reactfuscator.model.ArchiveModel;
import java.util.List;

/** JNA binds Java field/function names at runtime, even without ACC_NATIVE bytecode methods. */
public final class NativeInteropAnalyzer {
    public void analyze(ArchiveModel archive,KeepPolicy keeps,HierarchyService hierarchy) {
        List<String> contracts=List.of("com/sun/jna/Structure","com/sun/jna/Union","com/sun/jna/Library","com/sun/jna/Callback");
        for(String owner:archive.classes().keySet())for(String contract:contracts)if(hierarchy.assignable(contract,owner)){keeps.keepMembersOf(owner);break;}
    }
}

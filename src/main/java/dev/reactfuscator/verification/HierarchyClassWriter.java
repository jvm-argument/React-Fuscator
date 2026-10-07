package dev.reactfuscator.verification;

import dev.reactfuscator.analysis.HierarchyService;
import org.objectweb.asm.ClassWriter;

public final class HierarchyClassWriter extends ClassWriter {
    private final HierarchyService hierarchy;
    public HierarchyClassWriter(HierarchyService hierarchy) { super(COMPUTE_FRAMES|COMPUTE_MAXS); this.hierarchy=hierarchy; }
    @Override protected String getCommonSuperClass(String a,String b) { return hierarchy.commonSuper(a,b); }
}

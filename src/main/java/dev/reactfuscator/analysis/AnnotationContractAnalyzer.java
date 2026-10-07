package dev.reactfuscator.analysis;

import org.objectweb.asm.tree.AnnotationNode;
import java.util.*;

/** Known annotation discovery systems inspect types/annotations, rather than Java member names. */
public final class AnnotationContractAnalyzer {
    public boolean requiresMemberName(List<AnnotationNode> annotations) {
        if(annotations==null)return false;
        return annotations.stream().anyMatch(a->!independent(a.desc));
    }
    private boolean independent(String descriptor) {
        return descriptor.startsWith("Lorg/jetbrains/annotations/") || descriptor.startsWith("Lorg/checkerframework/")
            || descriptor.startsWith("Lnet/fabricmc/api/") || descriptor.startsWith("Ljava/lang/")
            || Set.of("Lorg/bukkit/event/EventHandler;","Lcom/google/common/eventbus/Subscribe;","Lcom/google/common/eventbus/AllowConcurrentEvents;","Lcom/google/gson/annotations/SerializedName;","Lcom/google/gson/annotations/Expose;").contains(descriptor);
    }
}

package dev.reactfuscator.analysis.leak;

import dev.reactfuscator.config.ObfuscationConfig;
import dev.reactfuscator.model.*;
import org.objectweb.asm.tree.*;
import java.util.*;
import java.util.regex.Pattern;

public final class LeakSnapshotFactory {
    private final DebugAttributeInspector debug;
    public LeakSnapshotFactory(DebugAttributeInspector debug){this.debug=debug;}
    public LeakSnapshot capture(ArchiveModel archive,ObfuscationConfig config){
        Set<String> classes=new TreeSet<>(archive.classes().keySet()),methods=new TreeSet<>(),fields=new TreeSet<>(),literals=new TreeSet<>(),sensitive=new TreeSet<>();Map<String,Long> attributes=new TreeMap<>();List<Pattern> patterns=config.sensitiveStringPatterns.stream().map(Pattern::compile).toList();
        for(ClassModel model:archive.classes().values()){
            debug.count(model.node()).forEach((key,count)->attributes.merge(key,count,Long::sum));
            for(FieldNode field:model.node().fields){fields.add(field.name);if(field.value instanceof String text && !text.isEmpty())literals.add(text);}
            for(MethodNode method:model.node().methods){if(!method.name.startsWith("<"))methods.add(method.name);for(AbstractInsnNode instruction:method.instructions){if(instruction instanceof LdcInsnNode literal && literal.cst instanceof String text && !text.isEmpty())literals.add(text);if(instruction instanceof InvokeDynamicInsnNode dynamic)for(Object argument:dynamic.bsmArgs)if(argument instanceof String text && !text.isEmpty())literals.add(text);}}
        }
        for(String text:literals)if(patterns.stream().anyMatch(p->p.matcher(text).find()))sensitive.add(text);
        return new LeakSnapshot(Set.copyOf(classes),Set.copyOf(methods),Set.copyOf(fields),Set.copyOf(literals),Set.copyOf(sensitive),Map.copyOf(attributes),new LinkedHashMap<>(archive.resources()));
    }
}

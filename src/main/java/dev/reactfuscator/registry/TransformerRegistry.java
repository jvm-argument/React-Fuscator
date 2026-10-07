package dev.reactfuscator.registry;

import dev.reactfuscator.transform.Transformer;
import java.util.*;

public final class TransformerRegistry {
    private final Map<String,Transformer> transformers=new LinkedHashMap<>();
    public TransformerRegistry(List<Transformer> defaults) { defaults.forEach(this::register); ServiceLoader.load(Transformer.class).forEach(this::register); }
    public void register(Transformer transformer) { if(transformers.putIfAbsent(transformer.descriptor().id(),transformer)!=null) throw new IllegalArgumentException("Duplicate transformer: "+transformer.descriptor().id()); }
    public List<Transformer> ordered() { return transformers.values().stream().sorted(Comparator.comparingInt(t->t.descriptor().order())).toList(); }
    public Set<String> ids() { return Set.copyOf(transformers.keySet()); }
}

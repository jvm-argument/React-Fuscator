package dev.reactfuscator.model;

import java.util.*;

public final class RunStatistics {
    public int classes;
    public int generatedClasses;
    public int outputClasses;
    public int renamedClasses;
    public int renamedMethods;
    public int renamedFields;
    public long inputBytes;
    public long outputBytes;
    public long elapsedMillis;
    public long seed;
    public String profile;
    public Map<String,Long> cipherVariants=new LinkedHashMap<>();
    public Map<String,Long> flowVariants=new LinkedHashMap<>();
    public transient Set<String> transformedMethodKeys=new HashSet<>();
    public ProtectionReport protection;
    public Map<String, Long> transformations = new LinkedHashMap<>();
    public Map<String, String> keptClasses = new TreeMap<>();
    public List<String> warnings = new ArrayList<>();
    public void changed(String id) { transformations.merge(id, 1L, Long::sum); }
}

package dev.reactfuscator.model;

import java.util.*;

public final class ProtectionReport {
    public int renamedClasses;
    public int renamedMethods;
    public int renamedFields;
    public int transformedMethods;

    public long encryptedStrings;
    public long removedDebugAttributes;
    public long generatedOpaquePredicates;
    public long generatedProxies;
    public long generatedDispatchers;

    public long metadataLeaksFound;
    public long metadataLeaksRemaining;
    public long metadataLeaksResolved;

    public Map<String, Long> removedDebugAttributesByType = new TreeMap<>();
    public Map<String, Long> findingsByCategory = new TreeMap<>();
    public Map<String, Long> findingsByStatus = new TreeMap<>();

    public long totalFindings;
    public long omittedDetails;

    public List<LeakFinding> leaks = new ArrayList<>();
}

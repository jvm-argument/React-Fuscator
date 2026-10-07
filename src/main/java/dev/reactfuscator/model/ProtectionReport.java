package dev.reactfuscator.model;

import java.util.*;

public final class ProtectionReport {
    public int renamedClasses, renamedMethods, renamedFields, transformedMethods;
    public long encryptedStrings,
            removedDebugAttributes,
            generatedOpaquePredicates,
            generatedProxies,
            generatedDispatchers;
    public long metadataLeaksFound, metadataLeaksRemaining, metadataLeaksResolved;
    public Map<String, Long> removedDebugAttributesByType = new TreeMap<>();
    public Map<String, Long> findingsByCategory = new TreeMap<>(),
            findingsByStatus = new TreeMap<>();
    public long totalFindings, omittedDetails;
    public List<LeakFinding> leaks = new ArrayList<>();
}

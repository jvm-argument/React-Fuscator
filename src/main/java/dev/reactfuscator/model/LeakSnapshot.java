package dev.reactfuscator.model;

import java.util.*;

/** Original tokens/debug attributes/resources captured before any mutation. */
public record LeakSnapshot(Set<String> classes,Set<String> methods,Set<String> fields,Set<String> literals,Set<String> sensitive,
                           Map<String,Long> debugAttributes,Map<String,byte[]> resources) {}

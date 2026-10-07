package dev.reactfuscator.model;

import java.nio.file.Path;

public record ObfuscationResult(Path output, Path mapping, Path report, RunStatistics statistics) {}

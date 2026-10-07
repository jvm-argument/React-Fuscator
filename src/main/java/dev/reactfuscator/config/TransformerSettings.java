package dev.reactfuscator.config;

import java.util.List;

public final class TransformerSettings {
    public boolean enabled = true;
    public int density = -1;
    public int rounds = -1;
    public List<String> exclude = List.of();
    public int density(ProtectionProfile profile) { return density < 0 ? profile.density() : density; }
    public int rounds(ProtectionProfile profile) { return rounds < 0 ? profile.rounds() : rounds; }
}

package dev.reactfuscator.config;

public enum ProtectionProfile {
    LIGHT(1, 15), NORMAL(2, 35), STRONG(3, 65), EXTREME(4, 100);
    private final int rounds;
    private final int density;
    ProtectionProfile(int rounds, int density) { this.rounds = rounds; this.density = density; }
    public int rounds() { return rounds; }
    public int density() { return density; }
}

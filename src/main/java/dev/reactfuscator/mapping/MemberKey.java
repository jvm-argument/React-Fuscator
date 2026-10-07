package dev.reactfuscator.mapping;

public record MemberKey(String owner, String name, String descriptor) {
    public String text() { return owner + "#" + name + " " + descriptor; }
}

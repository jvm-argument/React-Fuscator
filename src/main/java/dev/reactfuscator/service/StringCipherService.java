package dev.reactfuscator.service;

import dev.reactfuscator.runtime.StringCipherTemplate;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.io.*;

public final class StringCipherService {
    public String encrypt(String plain, int key) {
        StringBuilder text = new StringBuilder(plain.length() * 4);
        for (int i = 0; i < plain.length(); i++) {
            int stream = Integer.rotateLeft(key + i * 0x9e3779b9, i & 31) ^ (key >>> 16) ^ i;
            int value = (plain.charAt(i) ^ stream) & 0xffff;
            for (int shift = 12; shift >= 0; shift -= 4) {
                text.append(Character.forDigit((value >>> shift) & 15, 16));
            }
        }
        return text.toString();
    }

    public String encrypt(String plain, int key, int variant) {
        if (variant == 0) {
            return encrypt(plain, key);
        }
        char[] encoded = new char[plain.length()];
        int state = key;
        for (int i = 0; i < encoded.length; i++) {
            int value = plain.charAt(i);
            switch (variant) {
                case 1 -> {
                    int mask = Integer.rotateRight(key ^ i * 0x6d2b79f5, (i + key) & 31);
                    encoded[i] = (char) (value ^ mask);
                }
                case 2 -> {
                    int rotation = ((key >>> 27) + i) % 15 + 1;
                    int rotated = ((value << rotation) | (value >>> (16 - rotation))) & 65535;
                    int mask = Integer.rotateLeft(key + (i + 1) * 0x1b873593, i & 31);
                    encoded[encoded.length - i - 1] = (char) (rotated + mask);
                }
                case 3 -> {
                    int mask = Integer.rotateLeft(state + i * 0x9e3779b9, 7);
                    int word = (value ^ mask ^ (mask >>> 16)) & 65535;
                    encoded[i] = (char) word;
                    state = Integer.rotateLeft(state ^ word ^ i, 5) + 0x7f4a7c15;
                }
                default -> throw new IllegalArgumentException("Unknown cipher variant: " + variant);
            }
        }
        return new String(encoded);
    }

    public MethodNode decoder(String name) {
        return decoder(name, 0);
    }

    public MethodNode decoder(String name, int variant) {
        String path = "/" + StringCipherTemplate.class.getName().replace('.', '/') + ".class";
        try (InputStream in = StringCipherTemplate.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("String cipher template missing");
            }
            ClassNode c = new ClassNode(Opcodes.ASM9);
            new ClassReader(in).accept(c, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            String entry =
                    switch (variant) {
                        case 0 -> "decode";
                        case 1 -> "reverseXor";
                        case 2 -> "rotateSubtract";
                        case 3 -> "chained";
                        default -> throw new IllegalArgumentException("Unknown cipher variant");
                    };
            MethodNode method =
                    c.methods.stream().filter(m -> m.name.equals(entry)).findFirst().orElseThrow();
            method.name = name;
            method.access = Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC;
            return method;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read string cipher template", e);
        }
    }
}

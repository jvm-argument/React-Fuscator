package dev.reactfuscator.runtime;

public final class StringCipherTemplate {
    private StringCipherTemplate() {
    }

    public static String decode(String text, int key) {
        char[] chars = new char[text.length() / 4];
        for (int i = 0; i < chars.length; i++) {
            int offset = i * 4;
            int encrypted =
                    (Character.digit(text.charAt(offset), 16) << 12)
                            | (Character.digit(text.charAt(offset + 1), 16) << 8)
                            | (Character.digit(text.charAt(offset + 2), 16) << 4)
                            | Character.digit(text.charAt(offset + 3), 16);
            int stream = Integer.rotateLeft(key + i * 0x9e3779b9, i & 31) ^ (key >>> 16) ^ i;
            chars[i] = (char) (encrypted ^ stream);
        }
        return new String(chars).intern();
    }

    public static String reverseXor(String text, int key) {
        char[] data = text.toCharArray();
        for (int i = data.length - 1; i >= 0; --i) {
            int mask = Integer.rotateRight(key ^ i * 0x6d2b79f5, (i + key) & 31);
            data[i] = (char) (data[i] ^ mask);
        }
        return new String(data).intern();
    }

    public static String rotateSubtract(String text, int key) {
        char[] result = new char[text.length()];
        int i = 0;
        while (i < result.length) {
            int mask = Integer.rotateLeft(key + (i + 1) * 0x1b873593, i & 31);
            int word = (text.charAt(result.length - i - 1) - mask) & 65535;
            int rotation = ((key >>> 27) + i) % 15 + 1;
            result[i] = (char) ((word >>> rotation) | (word << (16 - rotation)));
            i++;
        }
        return new String(result).intern();
    }

    public static String chained(String text, int key) {
        StringBuilder result = new StringBuilder(text.length());
        int state = key, index = 0;
        while (index < text.length()) {
            int word = text.charAt(index);
            int mask = Integer.rotateLeft(state + index * 0x9e3779b9, 7);
            result.append((char) (word ^ mask ^ (mask >>> 16)));
            state = Integer.rotateLeft(state ^ word ^ index, 5) + 0x7f4a7c15;
            index++;
        }
        return result.toString().intern();
    }
}

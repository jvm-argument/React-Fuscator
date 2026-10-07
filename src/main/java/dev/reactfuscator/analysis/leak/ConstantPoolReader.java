package dev.reactfuscator.analysis.leak;

import java.io.*;
import java.util.*;

public final class ConstantPoolReader {
    public record Pool(List<String> utf8, Set<String> literals) {
    }

    public List<String> strings(byte[] bytes) throws IOException {
        return read(bytes).utf8();
    }

    public Pool read(byte[] bytes) throws IOException {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt() != 0xcafebabe) {
                throw new IOException("Invalid class magic");
            }
            input.readUnsignedShort();
            input.readUnsignedShort();
            int count = input.readUnsignedShort();
            List<String> strings = new ArrayList<>();
            String[] values = new String[count];
            List<Integer> references = new ArrayList<>();
            for (int index = 1; index < count; index++) {
                switch (input.readUnsignedByte()) {
                    case 1 -> {
                        values[index] = input.readUTF();
                        strings.add(values[index]);
                    }
                    case 3, 4 -> input.skipNBytes(4);
                    case 5, 6 -> {
                        input.skipNBytes(8);
                        index++;
                    }
                    case 8 -> references.add(input.readUnsignedShort());
                    case 7, 16, 19, 20 -> input.skipNBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
                    case 15 -> input.skipNBytes(3);
                    default -> throw new IOException("Unknown constant pool tag at " + index);
                }
            }
            Set<String> literals = new HashSet<>();
            for (int reference : references) {
                if (reference <= 0 || reference >= count || values[reference] == null) {
                    throw new IOException("Invalid CONSTANT_String reference");
                }
                literals.add(values[reference]);
            }
            return new Pool(strings, literals);
        }
    }
}

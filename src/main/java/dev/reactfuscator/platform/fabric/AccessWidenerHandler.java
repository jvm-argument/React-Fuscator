package dev.reactfuscator.platform.fabric;

import dev.reactfuscator.mapping.MappingModel;
import dev.reactfuscator.remap.SymbolRemapper;

import java.nio.charset.StandardCharsets;

public final class AccessWidenerHandler {
    public byte[] remap(byte[] bytes, MappingModel mapping) {
        SymbolRemapper remapper = new SymbolRemapper(mapping);
        StringBuilder out = new StringBuilder();
        for (String line : new String(bytes, StandardCharsets.UTF_8).split("\\R", -1)) {
            int hash = line.indexOf('#');
            String comment = hash < 0 ? "" : line.substring(hash);
            String body = (hash < 0 ? line : line.substring(0, hash)).strip();
            String[] p = body.split("\\s+");
            if (p.length >= 3
                    && (p[1].equals("class") || p[1].equals("method") || p[1].equals("field"))) {
                String owner = p[2];
                p[2] = mapping.mapClass(owner);
                if (p.length >= 5) {
                    p[3] =
                            p[1].equals("method")
                                    ? mapping.mapMethod(owner, p[3], p[4])
                                    : mapping.mapField(owner, p[3], p[4]);
                    p[4] =
                            p[1].equals("method")
                                    ? remapper.mapMethodDesc(p[4])
                                    : remapper.mapDesc(p[4]);
                }
                out.append(String.join("\t", p));
                if (!comment.isEmpty()) {
                    out.append(' ').append(comment);
                }
            } else {
                out.append(line);
            }
            out.append('\n');
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}

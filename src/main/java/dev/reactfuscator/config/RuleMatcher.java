package dev.reactfuscator.config;

import java.util.*;
import java.util.regex.Pattern;

public final class RuleMatcher {
    private final List<Pattern> patterns;

    public RuleMatcher(List<String> globs) {
        patterns = globs.stream().map(this::compile).toList();
    }

    public boolean matches(String name) {
        return patterns.stream().anyMatch(p -> p.matcher(name).matches());
    }

    private Pattern compile(String glob) {
        StringBuilder out = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    if (i + 2 < glob.length() && glob.charAt(i + 2) == '/') {
                        out.append("(?:.*/)?");
                        i += 2;
                    } else {
                        out.append(".*");
                        i++;
                    }
                } else {
                    out.append("[^/]*");
                }
            } else if (c == '?') {
                out.append("[^/]");
            } else {
                out.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return Pattern.compile(out.append('$').toString());
    }
}

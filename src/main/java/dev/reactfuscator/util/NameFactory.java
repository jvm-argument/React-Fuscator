package dev.reactfuscator.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.SplittableRandom;

public final class NameFactory {
    private final SplittableRandom random;
    private final Set<String> issued = new HashSet<>();

    public NameFactory(SplittableRandom random) {
        this.random = random;
    }

    public void reserve(Collection<String> names) {
        issued.addAll(names);
    }

    public String next() {
        String name;
        do {
            name = "_" + Long.toUnsignedString(random.nextLong(), 36);
        } while (!issued.add(name));
        return name;
    }
}

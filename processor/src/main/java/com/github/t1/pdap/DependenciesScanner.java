package com.github.t1.pdap;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

class DependenciesScanner {
    static Set<Entry<CharSequence, CharSequence>> mockClassDependencies;
    private Function<CharSequence, InputStream> resolver;

    DependenciesScanner(Function<CharSequence, InputStream> resolver) { this.resolver = resolver; }

    Stream<CharSequence> scan(CharSequence name) {
        if (mockClassDependencies != null)
            return mockClassDependencies.stream()
                .filter(it -> name.equals(it.getKey()))
                .map(Entry::getValue);
        try (InputStream inputStream = resolver.apply(name)) {
            return Stream.empty();
        } catch (IOException e) {
            throw new RuntimeException("can't scan " + name, e);
        }
    }
}

package com.github.t1.pdap;

import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

class DependenciesScanner {
    static Set<Entry<CharSequence, CharSequence>> mockClassDependencies;

    Stream<CharSequence> scan(CharSequence name) {
        return mockClassDependencies.stream()
            .filter(it -> name.equals(it.getKey()))
            .map(Entry::getValue);
    }
}

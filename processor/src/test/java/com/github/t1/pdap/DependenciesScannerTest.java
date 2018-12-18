package com.github.t1.pdap;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashSet;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

class DependenciesScannerTest {
    private final DependenciesScanner scanner = new DependenciesScanner(this::resolve);

    private InputStream resolve(CharSequence name) {
        return null;
    }

    @Test
    void shouldScanNoOutputFileManager() {
        DependenciesScanner.mockClassDependencies = new HashSet<>(asList(
            new SimpleEntry<>("com.github.t1.pdap.NoOutputFileManager", "java.util.Map.Entry"),
            new SimpleEntry<>("com.github.t1.pdap.NoOutputFileManager", "java.util.Set"),
            new SimpleEntry<>("com.github.t1.pdap.NoOutputFileManager", "java.util.stream.Stream")
        ));

        Stream<CharSequence> dependencies = scanner.scan(NoOutputFileManager.class.getName());

        assertThat(dependencies).containsOnly(
            "java.util.Map.Entry",
            "java.util.Set",
            "java.util.stream.Stream");
    }
}

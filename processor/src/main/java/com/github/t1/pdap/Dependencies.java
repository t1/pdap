package com.github.t1.pdap;

import javax.lang.model.element.PackageElement;
import javax.lang.model.util.Elements;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

class Dependencies {
    private static SimpleEntry<String, String> entry(String source, String target) {
        return new SimpleEntry<>(source, target);
    }

    private final Elements elements;
    private final Map<String, Set<String>> primary = new HashMap<>();
    private final Map<String, Set<String>> all = new HashMap<>();
    private final Set<Entry<String, PackageElement>> invalid = new HashSet<>();
    private final Set<Entry<String, String>> forbidden = new HashSet<>();
    private final Set<Entry<String, String>> cycles = new HashSet<>();

    Dependencies(Elements elements) {
        this.elements = elements;
    }

    boolean missing(String packageElement) { return dependencies(packageElement) == null; }

    private Set<String> dependencies(String packageElement) {
        return all.computeIfAbsent(packageElement, e -> allowedPackageDependencies(packageElement));
    }

    private Set<String> allowedPackageDependencies(String source) {
        DependsOnCollector collector = new DependsOnCollector(source);
        if (collector.all != null && collector.all.remove(source)) {
            collector.primary.remove(source);
            cycles.add(entry(source, source));
        }
        if (collector.primary != null)
            this.primary.put(source, collector.primary);
        return collector.all;
    }

    void use(String source, String target) {
        if (all.containsKey(source)) {
            if (all.get(source).remove(target)) {
                if (primary.containsKey(source))
                    primary.get(source).remove(target);
                return;
            }
        }
        forbidden.add(entry(source, target));
    }

    Stream<Entry<String, PackageElement>> invalid() { return invalid.stream(); }

    Stream<Entry<String, String>> forbidden() { return forbidden.stream(); }

    Stream<Entry<String, String>> cycles() { return cycles.stream(); }

    Stream<Entry<String, String>> unused() {
        Set<Entry<String, String>> result = new HashSet<>();
        for (Entry<String, Set<String>> entry : primary.entrySet()) {
            for (String target : entry.getValue()) {
                result.add(entry(entry.getKey(), target));
            }
        }
        return result.stream();
    }

    private class DependsOnCollector {
        private final String packageElement;

        Set<String> primary;
        Set<String> all;

        DependsOnCollector(String packageElement) {
            this.packageElement = packageElement;
            scanAllDependsOn();
        }

        private void scanAllDependsOn() {
            String qualifiedName = packageElement;
            scanDependsOn(qualifiedName);
            if (all != null)
                primary = new HashSet<>(all);
            while (qualifiedName.contains(".")) {
                qualifiedName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
                scanDependsOn(qualifiedName);
            }
        }

        private void scanDependsOn(String qualifiedName) {
            PackageElement element = elements.getPackageElement(qualifiedName);
            if (element == null)
                return;
            DependsOn annotation = element.getAnnotation(DependsOn.class);
            if (annotation != null) {
                if (all == null)
                    all = new HashSet<>();
                all.addAll(resolveDependsOn(annotation, element));
            }
        }

        private List<String> resolveDependsOn(DependsOn annotation, PackageElement packageElement) {
            List<String> allowedDependencies = new ArrayList<>();
            for (String dependency : annotation.value()) {
                if (dependency.isEmpty())
                    continue;
                PackageElement dependencyElement = elements.getPackageElement(dependency);
                if (dependencyElement == null) {
                    invalid.add(new SimpleEntry<>(dependency, packageElement));
                } else {
                    allowedDependencies.add(dependencyElement.getQualifiedName().toString());
                }
            }
            return allowedDependencies;
        }
    }
}

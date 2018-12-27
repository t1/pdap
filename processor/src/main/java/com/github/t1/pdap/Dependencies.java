package com.github.t1.pdap;

import javax.lang.model.element.Element;
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
    private final Elements elements;
    private final Map<String, Set<String>> primary = new HashMap<>();
    private final Map<String, Set<String>> all = new HashMap<>();
    private final Set<Entry<Element, String>> invalid = new HashSet<>();
    private final List<Entry<Element, String>> forbidden = new ArrayList<>();
    private final Set<Entry<Element, String>> cycles = new HashSet<>();

    Dependencies(Elements elements) {
        this.elements = elements;
    }

    boolean missing(String packageElement) { return dependencies(packageElement) == null; }

    private Set<String> dependencies(String packageElement) {
        return all.computeIfAbsent(packageElement, e -> allowed(packageElement));
    }

    private Set<String> allowed(String source) {
        DependsOnCollector collector = new DependsOnCollector(source);
        if (collector.all != null && collector.all.remove(source)) {
            collector.primary.remove(source);
            cycles.add(entry(source, source));
        }
        if (collector.primary != null)
            this.primary.put(source, collector.primary);
        return collector.all;
    }

    void use(String source, String target, Element element) {
        if (all.containsKey(source)) {
            if (all.get(source).remove(target)) {
                if (primary.containsKey(source))
                    primary.get(source).remove(target);
            }
            return;
        }
        forbidden.add(entry(element, target));
    }

    Stream<Entry<Element, String>> invalid() { return invalid.stream(); }

    Stream<Entry<Element, String>> forbidden() { return forbidden.stream(); }

    Stream<Entry<Element, String>> cycles() { return cycles.stream(); }

    Stream<Entry<Element, String>> unused() {
        Set<Entry<Element, String>> result = new HashSet<>();
        for (Entry<String, Set<String>> entry : primary.entrySet()) {
            for (String target : entry.getValue()) {
                result.add(entry(entry.getKey(), target));
            }
        }
        return result.stream();
    }

    private class DependsOnCollector {
        Set<String> primary;
        Set<String> all;

        DependsOnCollector(String qualifiedName) {
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

        private List<String> resolveDependsOn(DependsOn annotation, PackageElement source) {
            List<String> allowed = new ArrayList<>();
            for (String target : annotation.value()) {
                if (target.isEmpty())
                    continue;
                PackageElement targetElement = elements.getPackageElement(target);
                if (targetElement == null) {
                    invalid.add(entry(source, target));
                } else {
                    allowed.add(targetElement.getQualifiedName().toString());
                }
            }
            return allowed;
        }
    }

    private SimpleEntry<Element, String> entry(Element source, String target) {
        return new SimpleEntry<>(source, target);
    }

    private SimpleEntry<Element, String> entry(String source, String target) {
        return new SimpleEntry<>(elements.getPackageElement(target), source);
    }
}

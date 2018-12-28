package com.github.t1.pdap;

import com.github.t1.pdap.Dependencies.Dependency.Type;

import javax.lang.model.element.PackageElement;
import javax.lang.model.util.Elements;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

import static com.github.t1.pdap.Dependencies.Dependency.Type.CYCLE;
import static com.github.t1.pdap.Dependencies.Dependency.Type.FORBIDDEN;
import static com.github.t1.pdap.Dependencies.Dependency.Type.INVALID;
import static com.github.t1.pdap.Dependencies.Dependency.Type.PRIMARY;
import static com.github.t1.pdap.Dependencies.Dependency.Type.SECONDARY;

class Dependencies {
    static class Dependency {
        enum Type {
            PRIMARY, SECONDARY, INVALID, FORBIDDEN, CYCLE;

            public Dependency dependency(String source, String target) { return new Dependency(source, target, this); }
        }

        final String source;
        final String target;
        final Type type;

        boolean used = false;

        Dependency(String source, String target, Type type) {
            this.source = source;
            this.target = target;
            this.type = type;
        }
    }

    private final Elements elements;
    final List<Dependency> dependencies = new ArrayList<>();
    private final List<PackageElement> missingDependsOns = new ArrayList<>();

    Dependencies(Elements elements) {
        this.elements = elements;
    }

    void scan(String source) {
        DependsOnCollector collector = new DependsOnCollector(source);
        if (collector.all == null) {
            missingDependsOns.add(elements.getPackageElement(source));
        } else {
            collector.all.forEach(target -> {
                Type type = source.equals(target) ? CYCLE : collector.isPrimary(target) ? PRIMARY : SECONDARY;
                dependencies.add(type.dependency(source, target));
            });
            collector.invalid.forEach(invalid -> dependencies.add(INVALID.dependency(invalid.getKey(), invalid.getValue())));
        }
    }

    void use(String source, String target) {
        dependency(source, target).used = true;
    }

    private Dependency dependency(String source, String target) {
        return dependencies.stream()
            .filter(dependency -> dependency.target.equals(target))
            .filter(dependency -> dependency.source.equals(source))
            .findAny()
            .orElseGet(() -> FORBIDDEN.dependency(source, target));
    }

    Stream<PackageElement> missing() { return missingDependsOns.stream(); }

    private class DependsOnCollector {
        Set<String> primary;
        Set<String> all;
        List<Entry<String, String>> invalid = new ArrayList<>();

        DependsOnCollector(String source) {
            scanDependsOn(source);
            if (all != null)
                primary = new HashSet<>(all);
            while (source.contains(".")) {
                source = source.substring(0, source.lastIndexOf('.'));
                scanDependsOn(source);
            }
        }

        private void scanDependsOn(String source) {
            PackageElement element = elements.getPackageElement(source);
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
                    invalid.add(new SimpleEntry<>(source.getQualifiedName().toString(), target));
                } else {
                    allowed.add(targetElement.getQualifiedName().toString());
                }
            }
            return allowed;
        }

        boolean isPrimary(String target) { return primary != null && primary.contains(target); }
    }
}

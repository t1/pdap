package com.github.t1.pdap;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

import static java.util.Collections.addAll;

class DependsOnCollector {
    private final PackageElement packageElement;
    private final Elements elementUtils;
    private final BiConsumer<String, Element> error;
    private final Set<String> missingDependsOns;

    private Set<String> foundDependsOns;

    DependsOnCollector(PackageElement element, Elements elementUtils, BiConsumer<String, Element> error, Set<String> missingDependsOns) {
        packageElement = element;
        this.missingDependsOns = missingDependsOns;
        this.elementUtils = elementUtils;
        this.error = error;
    }

    List<String> getDependencies() {
        scanDependsOnInAll(packageElement);
        if (foundDependsOns == null)
            return null;
        return resolveDependencies();
    }

    private void scanDependsOnInAll(PackageElement element) {
        scanDependsOn(element);
        String qualifiedName = element.getQualifiedName().toString();
        while (qualifiedName.contains(".")) {
            qualifiedName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
            scanDependsOn(elementUtils.getPackageElement(qualifiedName));
        }
    }

    private void scanDependsOn(PackageElement element) {
        DependsOn annotation = element.getAnnotation(DependsOn.class);
        if (annotation == null) {
            missingDependsOns.add(element.getQualifiedName().toString());
        } else {
            if (foundDependsOns == null)
                foundDependsOns = new HashSet<>();
            addAll(foundDependsOns, annotation.value());
        }
    }

    private List<String> resolveDependencies() {
        List<String> allowedDependencies = new ArrayList<>();
        for (String dependency : foundDependsOns) {
            if (dependency.isEmpty())
                continue;
            PackageElement dependencyElement = elementUtils.getPackageElement(dependency);
            if (dependencyElement == null) {
                error.accept("Invalid @DependsOn: unknown package [" + dependency + "]", packageElement);
            } else {
                allowedDependencies.add(dependencyElement.getQualifiedName().toString());
            }
        }
        return allowedDependencies;
    }
}

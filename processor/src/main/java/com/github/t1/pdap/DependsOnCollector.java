package com.github.t1.pdap;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.util.Elements;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

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

    Set<String> getDependencies() {
        scanAllDependsOn(packageElement);
        if (foundDependsOns == null)
            return null;
        return foundDependsOns;
    }

    private void scanAllDependsOn(PackageElement element) {
        String qualifiedName = element.getQualifiedName().toString();
        scanDependsOn(qualifiedName);
        while (qualifiedName.contains(".")) {
            qualifiedName = qualifiedName.substring(0, qualifiedName.lastIndexOf('.'));
            scanDependsOn(qualifiedName);
        }
    }

    private void scanDependsOn(String qualifiedName) {
        PackageElement element = elementUtils.getPackageElement(qualifiedName);
        if (element == null)
            return;
        DependsOn annotation = element.getAnnotation(DependsOn.class);
        if (annotation == null) {
            missingDependsOns.add(element.getQualifiedName().toString());
        } else {
            if (foundDependsOns == null)
                foundDependsOns = new HashSet<>();
            foundDependsOns.addAll(resolveDependsOn(annotation, element));
        }
    }

    private List<String> resolveDependsOn(DependsOn annotation, PackageElement packageElement) {
        List<String> allowedDependencies = new ArrayList<>();
        for (String dependency : annotation.value()) {
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

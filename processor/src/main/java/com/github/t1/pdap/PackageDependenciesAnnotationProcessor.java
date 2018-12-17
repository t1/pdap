package com.github.t1.pdap;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.SourceVersion.RELEASE_11;

@SupportedSourceVersion(RELEASE_11)
@SupportedAnnotationTypes("com.github.t1.pdap.*")
public class PackageDependenciesAnnotationProcessor extends AbstractAnnotationProcessor {
    Set<Entry<CharSequence, CharSequence>> actualClassDependencies = new HashSet<>();
    private Map<Name, List<PackageElement>> actualPackageDependencies = new HashMap<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        other("process annotations " + annotations);
        Set<Entry<CharSequence, CharSequence>> dependencyErrors = new LinkedHashSet<>();
        Map<PackageElement, List<PackageElement>> unusedDependencies = new HashMap<>();
        for (Element element : roundEnv.getRootElements()) {
            other("process " + element.getKind() + " : " + element, element);
            if (element.getKind() == ElementKind.PACKAGE)
                continue;
            PackageElement packageElement = (PackageElement) element.getEnclosingElement();
            other("# " + packageElement.getQualifiedName());
            for (Element enclosedElement : packageElement.getEnclosedElements()) {
                other("-> " + enclosedElement.getKind() + ": " + enclosedElement);
            }
            List<PackageElement> allowedDependencies = allowedDependencies(element);
            List<PackageElement> unused = unusedDependencies.computeIfAbsent(packageElement, e -> new ArrayList<>(allowedDependencies));
            actualPackageDependencies((TypeElement) element).forEach(it -> {
                unused.remove(it);
                CharSequence source = packageElement.getQualifiedName();
                CharSequence target = it.getQualifiedName();
                if (allowedDependencies.contains(it)) {
                    other("allowed dependency [" + source + "] to [" + target + "]");
                } else {
                    dependencyErrors.add(new SimpleEntry<>(source, target));
                }
            });
        }
        dependencyErrors.forEach(it -> error("Forbidden dependency from [" + it.getKey() + "] to [" + it.getValue() + "]"));
        unusedDependencies.forEach((from, tos) -> tos.forEach(to -> warning("Unused dependency from [" + from + "] to [" + to + "]")));
        other("process end");
        return true;
    }

    private List<PackageElement> allowedDependencies(Element element) {
        List<PackageElement> allowedDependencies = new ArrayList<>();
        DependsUpon dependsUpon = findDependsUpon(element);
        if (dependsUpon != null) {
            List<String> packages = asList(dependsUpon.value());
            note("can depend upon " + packages, element);
            for (String dependency : packages) {
                if (dependency.isEmpty())
                    continue;
                PackageElement dependencyElement = processingEnv.getElementUtils().getPackageElement(dependency);
                if (dependencyElement == null) {
                    error("Invalid `DependsOn`: Unknown package [" + dependency + "].", element);
                } else {
                    allowedDependencies.add(dependencyElement);
                }
            }
        }
        return allowedDependencies;
    }

    private List<PackageElement> actualPackageDependencies(TypeElement element) {
        return actualPackageDependencies.computeIfAbsent(element.getQualifiedName(), name ->
            actualClassDependencies(element)
                .map(it -> (PackageElement) processingEnv.getElementUtils().getTypeElement(it).getEnclosingElement())
                .distinct()
                .collect(toList()));
    }

    private Stream<CharSequence> actualClassDependencies(TypeElement element) {
        return actualClassDependencies.stream()
            .filter(it -> element.getQualifiedName().contentEquals(it.getKey()))
            .map(Entry::getValue);
    }

    private DependsUpon findDependsUpon(Element element) {
        switch (element.getKind()) {
            case PACKAGE:
                return element.getAnnotation(DependsUpon.class);
            case ENUM:
            case CLASS:
            case ANNOTATION_TYPE:
            case INTERFACE:
                return findDependsUpon(/* PackageElement */ element.getEnclosingElement());
        }
        throw new UnsupportedOperationException("don't know how to find DependsUpon for " + element.getKind() + " " + element);
    }
}

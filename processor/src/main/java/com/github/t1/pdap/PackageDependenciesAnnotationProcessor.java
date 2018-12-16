package com.github.t1.pdap;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static javax.lang.model.SourceVersion.RELEASE_11;

@SupportedSourceVersion(RELEASE_11)
@SupportedAnnotationTypes("com.github.t1.pdap.*")
public class PackageDependenciesAnnotationProcessor extends AbstractAnnotationProcessor {
    Set<Entry<String, String>> actualDependencies;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        other("process annotations " + annotations);
        Set<Entry<String, String>> dependencyErrors = new LinkedHashSet<>();
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
            actualClassDependencies(packageElement).forEach(it -> {
                unused.remove(it);
                String source = packageElement.getQualifiedName().toString();
                String target = it.getQualifiedName().toString();
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

    private Stream<PackageElement> actualClassDependencies(PackageElement element) {
        if (actualDependencies == null)
            return Stream.empty();
        return actualDependencies.stream()
            .filter(it -> it.getKey().equals(element.getQualifiedName().toString()))
            .map(it -> processingEnv.getElementUtils().getPackageElement(it.getValue()));
    }

    // we do more casts than strictly necessary to document what's happening
    @SuppressWarnings("RedundantCast")
    private DependsUpon findDependsUpon(Element element) {
        switch (element.getKind()) {
            case PACKAGE:
                return element.getAnnotation(DependsUpon.class);
            case ENUM:
            case CLASS:
                return findDependsUpon((PackageElement) element.getEnclosingElement());
            case ANNOTATION_TYPE:
                break;
            case INTERFACE:
                break;
            case ENUM_CONSTANT:
                break;
            case FIELD:
                break;
            case PARAMETER:
                break;
            case LOCAL_VARIABLE:
                break;
            case EXCEPTION_PARAMETER:
                break;
            case METHOD:
                break;
            case CONSTRUCTOR:
                break;
            case STATIC_INIT:
                break;
            case INSTANCE_INIT:
                break;
            case TYPE_PARAMETER:
                break;
            case OTHER:
                break;
            case RESOURCE_VARIABLE:
                break;
            case MODULE:
                break;
        }
        error("don't know how to find DependsUpon for " + element.getKind() + " " + element);
        return null;
    }
}

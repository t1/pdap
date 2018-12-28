package com.github.t1.pdap;

import com.github.t1.pdap.Dependencies.Dependency;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.model.JavacElements;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Name;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static javax.lang.model.SourceVersion.RELEASE_8;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.WARNING;

@SupportedSourceVersion(RELEASE_8)
@SupportedAnnotationTypes("com.github.t1.pdap.*")
public class PackageDependenciesAnnotationProcessor extends AbstractAnnotationProcessor {
    private Map<Name, Set<String>> actualDependencies = new HashMap<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver())
            return false;
        Dependencies dependencies = new Dependencies(getElementUtils());
        for (Element element : roundEnv.getRootElements()) {
            if (!isType(element))
                continue;
            processType(dependencies, element);
        }
        report(dependencies);
        return true;
    }

    private boolean isType(Element element) {
        return element.getKind().isClass() || element.getKind().isInterface();
    }

    private void processType(Dependencies dependencies, Element element) {
        TypeElement typeElement = (TypeElement) element;
        PackageElement packageElement = (PackageElement) element.getEnclosingElement();
        String source = packageElement.getQualifiedName().toString();
        dependencies.scan(source);
        actualDependencies(typeElement).forEach(target -> dependencies.use(source, target));
    }

    private Set<String> actualDependencies(TypeElement element) {
        return actualDependencies.computeIfAbsent(element.getQualifiedName(), name ->
            new DependenciesCollector((JavacElements) getElementUtils(), (ClassSymbol) element).getDependencies());
    }

    private void report(Dependencies dependencies) {
        dependencies.dependencies.forEach(dependency -> {
            Entry<Kind, String> message = message(dependency);
            if (message != null)
                print(message.getKey(), message.getValue() + " [" + dependency.target + "]", getElementUtils().getPackageElement(dependency.source));
        });
        dependencies.missing().forEach(it -> print(WARNING, "no @DependsOn annotation", it));
    }

    private Entry<Kind, String> message(Dependency dependency) {
        switch (dependency.type) {
            case PRIMARY:
                return (dependency.used) ? null : entry(WARNING, "Unused dependency on");
            case SECONDARY:
                return null;
            case INVALID:
                return entry(ERROR, "Invalid @DependsOn: unknown package");
            case FORBIDDEN:
                return entry(ERROR, "Forbidden dependency on");
            case CYCLE:
                return entry(ERROR, "Cyclic dependency declared on");
        }
        throw new UnsupportedOperationException();
    }

    private Entry<Kind, String> entry(Kind kind, String message) { return new SimpleEntry<>(kind, message); }
}

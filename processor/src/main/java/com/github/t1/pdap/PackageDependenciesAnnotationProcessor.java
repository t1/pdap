package com.github.t1.pdap;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
    private List<PackageElement> missingDependsOns = new ArrayList<>();

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
        if (dependencies.missing(source)) {
            missingDependsOns.add(packageElement);
            return;
        }
        actualDependencies(typeElement).forEach(target -> dependencies.use(source, target, element));
    }

    private Set<String> actualDependencies(TypeElement element) {
        return actualDependencies.computeIfAbsent(element.getQualifiedName(), name ->
            new DependenciesCollector((JavacElements) processingEnv.getElementUtils(), (ClassSymbol) element).getDependencies());
    }

    private void report(Dependencies allowed) {
        allowed.invalid().forEach(it -> report(ERROR, "Invalid @DependsOn: unknown package", it));
        allowed.forbidden().forEach(it -> report(ERROR, "Forbidden dependency [" + it.getKey().getEnclosingElement().getSimpleName() + "] ->", it));
        allowed.cycles().forEach(it -> report(ERROR, "Cyclic dependency declared", it));
        allowed.unused().forEach(it -> report(WARNING, "Unused dependency [" + it.getKey() + "]", it));
        missingDependsOns.forEach(it -> print(WARNING, "no @DependsOn annotation", it));
    }

    private void report(Kind kind, String message, Entry<Element, String> entry) {
        print(kind, message + " [" + entry.getValue() + "]", entry.getKey());
    }
}

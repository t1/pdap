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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static javax.lang.model.SourceVersion.RELEASE_8;

@SupportedSourceVersion(RELEASE_8)
@SupportedAnnotationTypes("com.github.t1.pdap.*")
public class PackageDependenciesAnnotationProcessor extends AbstractAnnotationProcessor {
    private Map<Name, Set<String>> actualPackageDependencies = new HashMap<>();
    private Set<String> missingDependsOns = new TreeSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver())
            return false;
        Dependencies allowed = new Dependencies(getElementUtils());
        for (Element element : roundEnv.getRootElements()) {
            if (!isType(element))
                continue;
            TypeElement typeElement = (TypeElement) element;
            String source = ((PackageElement) element.getEnclosingElement()).getQualifiedName().toString();
            if (allowed.missing(source)) {
                missingDependsOns.add(source);
                continue;
            }
            actualPackageDependencies(typeElement).forEach(target -> allowed.use(source, target));
        }
        allowed.invalid().forEach(it -> error("Invalid @DependsOn: unknown package [" + it.getKey() + "]", it.getValue()));
        allowed.forbidden().forEach(it -> error("Forbidden dependency [" + it.getKey() + "] -> [" + it.getValue() + "]"));
        allowed.unused().forEach(it -> warning("Unused dependency [" + it.getKey() + "] -> [" + it.getValue() + "]"));
        allowed.cycles().forEach(it -> error("Cyclic dependency declared: [" + it.getKey() + "] -> [" + it.getValue() + "]"));
        missingDependsOns.forEach(it -> warning("no @DependsOn annotation on [" + it + "]"));
        return true;
    }

    private boolean isType(Element element) {
        return element.getKind().isClass() || element.getKind().isInterface();
    }

    private Set<String> actualPackageDependencies(TypeElement element) {
        return actualPackageDependencies.computeIfAbsent(element.getQualifiedName(), name ->
            new DependenciesCollector((JavacElements) processingEnv.getElementUtils(), (ClassSymbol) element).getDependencies());
    }
}

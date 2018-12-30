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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static javax.lang.model.SourceVersion.RELEASE_8;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.WARNING;

@SupportedSourceVersion(RELEASE_8)
@SupportedAnnotationTypes("com.github.t1.pdap.*")
public class PackageDependenciesAnnotationProcessor extends AbstractAnnotationProcessor {
    private Map<Name, Map<String, Element>> actualDependencies = new HashMap<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver())
            return false;
        Dependencies dependencies = new Dependencies(getElementUtils());
        for (Element element : roundEnv.getRootElements()) {
            if (!isType(element))
                continue;
            processType(dependencies, (TypeElement) element);
        }
        report(dependencies);
        return true;
    }

    private boolean isType(Element element) {
        return element.getKind().isClass() || element.getKind().isInterface();
    }

    private void processType(Dependencies dependencies, TypeElement typeElement) {
        PackageElement packageElement = getElementUtils().getPackageOf(typeElement);
        String source = packageElement.getQualifiedName().toString();
        dependencies.scan(source);
        actualDependencies(typeElement).forEach((target, element) -> dependencies.use((element == null) ? typeElement : element, source, target));
    }

    private Map<String, Element> actualDependencies(TypeElement element) {
        return actualDependencies.computeIfAbsent(element.getQualifiedName(), name -> {
            DependenciesCollector collector = new DependenciesCollector((JavacElements) getElementUtils(), (ClassSymbol) element);
            for (String extraImport : collector.extraImports)
                warning("Import [" + extraImport + "] not found as dependency", element);
            return collector.dependencies;
        });
    }

    private void report(Dependencies dependencies) {
        dependencies.stream().forEach(dependency -> {
            Message message = message(dependency);
            if (message != null)
                print(message.kind, message.message + " [" + dependency.target + "]", message.element);
        });
        dependencies.missing().forEach(it -> warning("no @DependsOn annotation", it));
    }

    private Message message(Dependency dependency) {
        switch (dependency.type) {
            case PRIMARY:
                return (dependency.used) ? null : new Message(WARNING, "Unused dependency on", element(dependency));
            case SECONDARY:
                return null;
            case INVALID:
                return new Message(ERROR, "Invalid @DependsOn: unknown package", element(dependency));
            case FORBIDDEN:
                return new Message(ERROR, "Forbidden dependency on", element(dependency));
            case INFERRED:
                return null;
            case CYCLE:
                return new Message(ERROR, "Cyclic dependency declared on", element(dependency));
        }
        throw new UnsupportedOperationException();
    }

    private Element element(Dependency dependency) {
        return (dependency.element == null) ? getElementUtils().getPackageElement(dependency.source) : dependency.element;
    }

    private class Message {
        private final Kind kind;
        private final String message;
        private final Element element;

        private Message(Kind kind, String message, Element element) {
            this.kind = kind;
            this.message = message;
            this.element = element;
        }
    }
}

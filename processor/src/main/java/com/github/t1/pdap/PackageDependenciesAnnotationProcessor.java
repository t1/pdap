package com.github.t1.pdap;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCImport;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Pair;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.Element;
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
import java.util.TreeSet;

import static java.util.Collections.emptySet;
import static javax.lang.model.SourceVersion.RELEASE_8;
import static javax.lang.model.element.ElementKind.PACKAGE;

@SupportedSourceVersion(RELEASE_8)
@SupportedAnnotationTypes("com.github.t1.pdap.*")
public class PackageDependenciesAnnotationProcessor extends AbstractAnnotationProcessor {
    private Map<Name, Set<String>> actualPackageDependencies = new HashMap<>();
    private Set<String> missingDependsOns = new TreeSet<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver())
            return false;
        Set<Entry<String, String>> dependencyErrors = new LinkedHashSet<>();
        Map<String, List<String>> unusedDependencies = new HashMap<>();
        for (Element element : roundEnv.getRootElements()) {
            if (!isType(element))
                continue;
            TypeElement typeElement = (TypeElement) element;
            String packageElement = ((PackageElement) element.getEnclosingElement()).getQualifiedName().toString();
            List<String> allowedDependencies = allowedDependencies(typeElement);
            if (allowedDependencies == null)
                continue;
            List<String> unused = unusedDependencies.computeIfAbsent(packageElement, e -> new ArrayList<>(allowedDependencies));
            actualPackageDependencies(typeElement).forEach(target -> {
                unused.remove(target);
                if (!allowedDependencies.contains(target)) {
                    dependencyErrors.add(new SimpleEntry<>(packageElement, target));
                }
            });
            if (unused.remove(packageElement)) {
                error("Cyclic dependency declared: [" + packageElement + "] -> [" + packageElement + "]");
            }
        }
        dependencyErrors.forEach(it -> error("Forbidden dependency [" + it.getKey() + "] -> [" + it.getValue() + "]"));
        unusedDependencies.forEach((from, tos) -> tos.forEach(to -> warning("Unused dependency [" + from + "] -> [" + to + "]")));
        missingDependsOns.forEach(it -> warning("no @DependsOn annotation on [" + it + "]"));
        return true;
    }

    private boolean isType(Element element) {
        return element.getKind().isClass() || element.getKind().isInterface();
    }

    private List<String> allowedDependencies(Element element) {
        switch (element.getKind()) {
            case PACKAGE:
                return allowedPackageDependencies((PackageElement) element);
            case ENUM:
            case CLASS:
            case ANNOTATION_TYPE:
            case INTERFACE:
                return allowedDependencies(/* PackageElement */ element.getEnclosingElement());
        }
        throw new UnsupportedOperationException("don't know how to find dependencies for " + element.getKind() + " " + element);
    }

    private List<String> allowedPackageDependencies(PackageElement packageElement) {
        return new DependsOnCollector(packageElement, getElementUtils(), this::error, missingDependsOns).getDependencies();
    }

    private Set<String> actualPackageDependencies(TypeElement element) {
        return actualPackageDependencies.computeIfAbsent(element.getQualifiedName(), name ->
            packagesDependenciesOf((ClassSymbol) element));
    }

    private Set<String> packagesDependenciesOf(ClassSymbol element) {
        return new DependenciesCollector((JavacElements) processingEnv.getElementUtils(), element).getDependencies();
    }
}

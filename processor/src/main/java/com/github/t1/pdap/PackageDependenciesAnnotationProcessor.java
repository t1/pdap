package com.github.t1.pdap;

import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.Pair;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.SourceVersion.RELEASE_8;

@SupportedSourceVersion(RELEASE_8)
@SupportedAnnotationTypes("com.github.t1.pdap.*")
public class PackageDependenciesAnnotationProcessor extends AbstractAnnotationProcessor {
    private Map<Name, List<PackageElement>> actualPackageDependencies = new HashMap<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver())
            return false;
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
        dependencyErrors.forEach(it -> error("Forbidden dependency [" + it.getKey() + "] -> [" + it.getValue() + "]"));
        unusedDependencies.forEach((from, tos) -> tos.forEach(to -> warning("Unused dependency [" + from + "] -> [" + to + "]")));
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
                PackageElement dependencyElement = getElementUtils().getPackageElement(dependency);
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
            resolve(element.getQualifiedName().toString())
                .map(this::packageOf)
                .distinct()
                .collect(toList()));
    }

    private Stream<CharSequence> resolve(CharSequence name) {
        final JavacElements elementUtils = (JavacElements) processingEnv.getElementUtils();
        ClassSymbol element = elementUtils.getTypeElement(name);
        Pair<JCTree, JCCompilationUnit> tree = elementUtils.getTreeAndTopLevel(element, null, null);
        if (tree == null || tree.snd == null)
            return Stream.empty();
        JCCompilationUnit compilationUnit = tree.snd;
        if (compilationUnit.getSourceFile().getName().endsWith("package-info.java"))
            return Stream.empty();
        // TODO get not only the imports but also the qualified names used within the code
        return compilationUnit.getImports().stream().map(jcImport -> jcImport.getQualifiedIdentifier().toString());
    }

    private PackageElement packageOf(CharSequence it) {
        return (PackageElement) getElementUtils().getTypeElement(it).getEnclosingElement();
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

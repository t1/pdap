package com.github.t1.pdap;

import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCImport;
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

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;
import static javax.lang.model.SourceVersion.RELEASE_11;

@SupportedSourceVersion(RELEASE_11)
@SupportedAnnotationTypes("com.github.t1.pdap.*")
public class PackageDependenciesAnnotationProcessor extends AbstractAnnotationProcessor {
    private Map<Name, List<PackageElement>> actualPackageDependencies = new HashMap<>();

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver())
            return false;
        Set<Entry<CharSequence, CharSequence>> dependencyErrors = new LinkedHashSet<>();
        Map<PackageElement, List<PackageElement>> unusedDependencies = new HashMap<>();
        for (Element element : roundEnv.getRootElements()) {
            if (!isType(element))
                continue;
            TypeElement typeElement = (TypeElement) element;
            PackageElement packageElement = (PackageElement) element.getEnclosingElement();
            List<PackageElement> allowedDependencies = allowedDependencies(typeElement);
            if (allowedDependencies == null)
                continue;
            List<PackageElement> unused = unusedDependencies.computeIfAbsent(packageElement, e -> new ArrayList<>(allowedDependencies));
            actualPackageDependencies(typeElement).forEach(it -> {
                unused.remove(it);
                CharSequence source = packageElement.getQualifiedName();
                CharSequence target = it.getQualifiedName();
                if (!allowedDependencies.contains(it)) {
                    dependencyErrors.add(new SimpleEntry<>(source, target));
                }
            });
            if (unused.remove(packageElement)) {
                error("Cyclic dependency declared: [" + packageElement.getQualifiedName() + "] -> [" + packageElement.getQualifiedName() + "]");
            }
        }
        dependencyErrors.forEach(it -> error("Forbidden dependency [" + it.getKey() + "] -> [" + it.getValue() + "]"));
        unusedDependencies.forEach((from, tos) -> tos.forEach(to -> warning("Unused dependency [" + from + "] -> [" + to + "]")));
        return true;
    }

    private boolean isType(Element element) {
        return element.getKind().isClass() || element.getKind().isInterface();
    }

    private List<PackageElement> allowedDependencies(TypeElement element) {
        List<PackageElement> allowedDependencies = new ArrayList<>();
        DependsUpon dependsUpon = findDependsUpon(element);
        if (dependsUpon == null) {
            note("no @DependsUpon annotation on [" + element + "]");
            return null;
        }
        for (String dependency : dependsUpon.value()) {
            if (dependency.isEmpty())
                continue;
            PackageElement dependencyElement = getElementUtils().getPackageElement(dependency);
            if (dependencyElement == null) {
                error("Invalid `DependsOn`: Unknown package [" + dependency + "].", element);
            } else {
                allowedDependencies.add(dependencyElement);
            }
        }
        return allowedDependencies;
    }

    private List<PackageElement> actualPackageDependencies(TypeElement element) {
        return actualPackageDependencies.computeIfAbsent(element.getQualifiedName(), name ->
            packagesDependenciesOf((ClassSymbol) element));
    }

    private List<PackageElement> packagesDependenciesOf(ClassSymbol element) {
        final JavacElements elementUtils = (JavacElements) processingEnv.getElementUtils();
        Pair<JCTree, JCCompilationUnit> tree = elementUtils.getTreeAndTopLevel(element, null, null);
        if (tree == null || tree.snd == null)
            return emptyList();
        JCCompilationUnit compilationUnit = tree.snd;
        if (compilationUnit.getSourceFile().getName().endsWith("package-info.java"))
            return emptyList();
        Set<CharSequence> packages = new HashSet<>();
        compilationUnit.accept(new TreeScanner() {
            @Override public void visitImport(JCImport tree) {
                packages.add(((JCFieldAccess) tree.getQualifiedIdentifier()).sym.owner.name);
            }

            @Override public void visitVarDef(JCVariableDecl tree) {
                if (tree.getType() instanceof JCIdent)
                    packages.add(((JCIdent) tree.getType()).sym.owner.name);
                else
                    packages.add(((JCFieldAccess) tree.getType()).sym.owner.name);
            }
        });
        packages.remove(element.packge().name);
        return packages.stream().map(this::toPackage).collect(toList());
    }

    private PackageElement toPackage(CharSequence it) {
        PackageElement packageElement = getElementUtils().getPackageElement(it);
        if (packageElement == null)
            throw new RuntimeException("package not found: [" + it + "]");
        return packageElement;
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

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

import static java.util.Collections.emptySet;
import static javax.lang.model.SourceVersion.RELEASE_8;
import static javax.lang.model.element.ElementKind.PACKAGE;

@SupportedSourceVersion(RELEASE_8)
@SupportedAnnotationTypes("com.github.t1.pdap.*")
public class PackageDependenciesAnnotationProcessor extends AbstractAnnotationProcessor {
    private Map<Name, Set<String>> actualPackageDependencies = new HashMap<>();

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
        return true;
    }

    private boolean isType(Element element) {
        return element.getKind().isClass() || element.getKind().isInterface();
    }

    private List<String> allowedDependencies(TypeElement element) {
        List<String> allowedDependencies = new ArrayList<>();
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
                allowedDependencies.add(dependencyElement.getQualifiedName().toString());
            }
        }
        return allowedDependencies;
    }

    private Set<String> actualPackageDependencies(TypeElement element) {
        return actualPackageDependencies.computeIfAbsent(element.getQualifiedName(), name ->
            packagesDependenciesOf((ClassSymbol) element));
    }

    private Set<String> packagesDependenciesOf(ClassSymbol element) {
        final JavacElements elementUtils = (JavacElements) processingEnv.getElementUtils();
        Pair<JCTree, JCCompilationUnit> tree = elementUtils.getTreeAndTopLevel(element, null, null);
        if (tree == null || tree.snd == null)
            return emptySet();
        JCCompilationUnit compilationUnit = tree.snd;
        if (compilationUnit.getSourceFile().getName().endsWith("package-info.java"))
            return emptySet();
        Set<String> packages = new HashSet<>();
        compilationUnit.accept(new TreeScanner() {
            private void addOwner(Symbol symbol) { addSymbol(symbol.owner); }

            private void addSymbol(Symbol symbol) {
                addName(toString(symbol));
            }

            private String toString(Symbol symbol) {
                return (isNullOrEmpty(symbol.owner)) ? symbol.name.toString() : toString(symbol.owner) + "." + symbol.name;
            }

            private void addName(String name) { packages.add(name); }

            private boolean isNullOrEmpty(Symbol symbol) { return symbol == null || symbol.name.isEmpty(); }

            @Override public void visitImport(JCImport tree) {
                addOwner(((JCFieldAccess) tree.getQualifiedIdentifier()).sym);
                super.visitImport(tree);
            }

            /** field */
            @Override public void visitVarDef(JCVariableDecl tree) {
                if (tree.getType() instanceof JCFieldAccess) {
                    JCFieldAccess fieldAccess = (JCFieldAccess) tree.getType();
                    if (isNullOrEmpty(fieldAccess.sym)) {
                        addName(((JCIdent) fieldAccess.selected).getName().toString());
                    } else
                        addOwner(fieldAccess.sym);
                }
                super.visitVarDef(tree);
            }

            @Override public void visitMethodDef(JCMethodDecl tree) {
                JCTree returnType = tree.getReturnType();
                if (returnType != null) {
                    if (tree.getReturnType() instanceof JCFieldAccess)
                        addOwner(((JCFieldAccess) tree.getReturnType()).sym);
                }
                super.visitMethodDef(tree);
            }

            @Override public void visitApply(JCMethodInvocation tree) {
                super.visitApply(tree);
            }

            @Override public void visitIdent(JCIdent tree) {
                if (!isNullOrEmpty(tree.sym) && !isNullOrEmpty(tree.sym.owner) && tree.sym.owner.getKind() == PACKAGE)
                    addOwner(tree.sym);
                super.visitIdent(tree);
            }

            @Override public void visitNewClass(JCNewClass tree) {
                if (tree.getIdentifier() instanceof JCIdent) {
                    if (((JCIdent) tree.getIdentifier()).sym != null)
                        addOwner(((JCIdent) tree.getIdentifier()).sym);
                } else {
                    addName(((JCIdent) ((JCFieldAccess) tree.getIdentifier()).selected).name.toString());
                }
                super.visitNewClass(tree);
            }
        });
        packages.remove(element.packge().name.toString());
        packages.remove("java.lang");
        return packages;
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

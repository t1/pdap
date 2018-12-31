package com.github.t1.pdap;

import com.sun.tools.javac.code.Attribute.Compound;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.model.JavacElements;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCClassDecl;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.tree.JCTree.JCExpression;
import com.sun.tools.javac.tree.JCTree.JCFieldAccess;
import com.sun.tools.javac.tree.JCTree.JCIdent;
import com.sun.tools.javac.tree.JCTree.JCImport;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Pair;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.util.Elements;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class DependenciesCollector {
    private final JavacElements elements;
    private final ClassSymbol classSymbol;
    private final JCCompilationUnit compilationUnit;

    /** The imports that could not be found as dependencies */
    final Set<String> extraImports = new HashSet<>();
    /** The dependencies found mapped to the first element that uses it */
    final Map<String, Element> dependencies = new HashMap<>();

    DependenciesCollector(Elements elements, Element classElement) {
        this.elements = (JavacElements) elements;
        this.classSymbol = (ClassSymbol) classElement;
        this.compilationUnit = compilationUnit();
        collect();
    }

    private JCCompilationUnit compilationUnit() {
        Pair<JCTree, JCCompilationUnit> tree = elements.getTreeAndTopLevel(classSymbol, null, null);
        if (tree == null || tree.snd == null)
            return null;
        return tree.snd;
    }

    private void collect() {
        if (compilationUnit == null)
            return;
        compilationUnit.accept(new TreeScanner() {
            private void addOwner(Symbol symbol, Element element) { addName(toString(symbol.owner), element); }

            private String toString(Symbol symbol) {
                return (isNullOrEmpty(symbol.owner)) ? symbol.name.toString() : toString(symbol.owner) + "." + symbol.name;
            }

            private void addName(String name, Element element) { dependencies.putIfAbsent(name, element); }

            private boolean isNullOrEmpty(Symbol symbol) { return symbol == null || symbol.name.isEmpty(); }

            private void removeAnnotationImports(Symbol symbol) {
                if (symbol != null && symbol.getMetadata() != null)
                    for (Compound attribute : symbol.getMetadata().getDeclarationAttributes())
                        extraImports.remove(toString(((ClassType) attribute.getAnnotationType()).tsym.owner));
            }

            @Override public void visitImport(JCImport tree) {
                extraImports.add(toString(((JCFieldAccess) tree.getQualifiedIdentifier()).sym.owner));
                super.visitImport(tree);
            }

            @Override public void visitClassDef(JCClassDecl classDecl) {
                removeAnnotationImports(classDecl.sym);
                if (classDecl.getExtendsClause() != null)
                    addOwner(((JCIdent) classDecl.getExtendsClause()).sym, classDecl.sym);
                for (JCExpression implementsClause : classDecl.getImplementsClause())
                    addOwner(((JCIdent) implementsClause).sym, classDecl.sym);
                for (JCTypeParameter typeParameter : classDecl.getTypeParameters())
                    for (JCExpression bound : typeParameter.getBounds())
                        addOwner(((JCIdent) bound).sym, classDecl.sym);
                super.visitClassDef(classDecl);
            }

            /** field */
            @Override public void visitVarDef(JCVariableDecl variable) {
                removeAnnotationImports(variable.sym);
                if (variable.getType() instanceof JCIdent) {
                    JCIdent ident = (JCIdent) variable.getType();
                    if (ident.sym != null) {
                        dependencies.computeIfAbsent(toString(ident.sym.owner), name -> variable.sym);
                    }
                } else if (variable.getType() instanceof JCFieldAccess) {
                    JCFieldAccess fieldAccess = (JCFieldAccess) variable.getType();
                    if (fieldAccess.sym == null) {
                        addName(((JCIdent) fieldAccess.selected).getName().toString(), variable.sym);
                    } else {
                        addOwner(fieldAccess.sym, fieldAccess.sym);
                    }
                }
                super.visitVarDef(variable);
            }

            @Override public void visitMethodDef(JCMethodDecl method) {
                JCTree returnType = method.getReturnType();
                if (returnType != null) {
                    if (returnType instanceof JCFieldAccess) {
                        addOwner(((JCFieldAccess) returnType).sym, method.sym);
                    } else if (returnType instanceof JCTypeApply) {
                        for (JCExpression typeArgument : ((JCTypeApply) returnType).getTypeArguments()) {
                            addOwner(((JCIdent) typeArgument).sym, method.sym);
                        }
                    } else if (returnType instanceof JCIdent) {
                        JCIdent identifier = (JCIdent) returnType;
                        resolveImport(identifier.getName()).ifPresent(symbol -> addOwner(symbol, method.sym));
                    }
                }
                super.visitMethodDef(method);
            }

            @Override public void visitNewClass(JCNewClass tree) {
                if (tree.getIdentifier() instanceof JCFieldAccess) {
                    JCFieldAccess identifier = (JCFieldAccess) tree.getIdentifier();
                    addName(((JCIdent) identifier.selected).name.toString(), identifier.sym);
                } else if (tree.getIdentifier() instanceof JCIdent) {
                    JCIdent identifier = (JCIdent) tree.getIdentifier();
                    resolveImport(identifier.getName()).ifPresent(symbol -> addOwner(symbol, identifier.sym));
                }
                super.visitNewClass(tree);
            }

            @Override public void visitApply(JCMethodInvocation methodInvocation) {
                JCExpression methodSelect = methodInvocation.getMethodSelect();
                if (methodSelect instanceof JCFieldAccess) {
                    JCFieldAccess fieldAccess = (JCFieldAccess) methodSelect;
                    if (fieldAccess.selected instanceof JCNewClass) {
                        JCNewClass selected = (JCNewClass) fieldAccess.selected;
                        JCIdent identifier = (JCIdent) selected.getIdentifier();
                        resolveImport(identifier.name).ifPresent(targetSymbol -> {
                            JCMethodDecl method = findMethod(targetSymbol, fieldAccess.name);
                            if (method != null && method.getReturnType() instanceof JCIdent) {
                                JCIdent returnType = (JCIdent) method.getReturnType();
                                PackageElement packageElement = elements.getPackageOf(returnType.sym);
                                addName(packageElement.getQualifiedName().toString(), null);
                            }
                        });
                    } else if (fieldAccess.selected instanceof JCIdent) {
                        JCIdent identifier = (JCIdent) fieldAccess.selected;
                        resolveImport(identifier.name).ifPresent(targetSymbol -> {
                            JCMethodDecl method = findMethod(targetSymbol, fieldAccess.name);
                            if (method != null) {
                                PackageElement packageElement = elements.getPackageOf(method.sym);
                                addName(packageElement.getQualifiedName().toString(), null);
                            }
                        });
                    }
                }
                super.visitApply(methodInvocation);
            }

            private Optional<Symbol> resolveImport(Name name) {
                return compilationUnit.getImports().stream()
                    .filter(i -> ((JCFieldAccess) i.getQualifiedIdentifier()).name.contentEquals(name))
                    .map(i -> ((JCFieldAccess) i.getQualifiedIdentifier()).sym)
                    .findAny();
            }

            private JCMethodDecl findMethod(Symbol type, Name methodName) {
                ClassSymbol targetElement = elements.getTypeElement(type.getQualifiedName());
                JCClassDecl targetClass = (JCClassDecl) elements.getTree(targetElement);
                for (JCTree member : targetClass.getMembers()) {
                    if (member instanceof JCMethodDecl && ((JCMethodDecl) member).name.contentEquals(methodName))
                        return (JCMethodDecl) member;
                }
                return null;
            }
        });
        dependencies.remove(classSymbol.packge().name.toString());
        dependencies.remove("java.lang");
        extraImports.removeAll(dependencies.keySet());
    }
}

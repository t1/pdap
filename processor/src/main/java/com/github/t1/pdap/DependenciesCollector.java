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

import java.util.HashSet;
import java.util.Set;

import static java.util.Collections.emptySet;
import static javax.lang.model.element.ElementKind.PACKAGE;

class DependenciesCollector {
    private final ClassSymbol classSymbol;
    private final JCCompilationUnit compilationUnit;

    DependenciesCollector(JavacElements elementUtils, ClassSymbol classSymbol) {
        this.classSymbol = classSymbol;
        this.compilationUnit = compilationUnit(elementUtils);
    }

    private JCCompilationUnit compilationUnit(JavacElements elementUtils) {
        Pair<JCTree, JCCompilationUnit> tree = elementUtils.getTreeAndTopLevel(classSymbol, null, null);
        if (tree == null || tree.snd == null)
            return null;
        JCCompilationUnit compilationUnit = tree.snd;
        if (compilationUnit.getSourceFile().getName().endsWith("package-info.java"))
            return null;
        return compilationUnit;
    }

    Set<String> getDependencies() {
        if (compilationUnit == null)
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
        packages.remove(classSymbol.packge().name.toString());
        packages.remove("java.lang");
        return packages;
    }
}

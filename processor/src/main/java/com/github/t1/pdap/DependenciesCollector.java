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
import com.sun.tools.javac.tree.JCTree.JCLiteral;
import com.sun.tools.javac.tree.JCTree.JCMethodDecl;
import com.sun.tools.javac.tree.JCTree.JCMethodInvocation;
import com.sun.tools.javac.tree.JCTree.JCNewClass;
import com.sun.tools.javac.tree.JCTree.JCPrimitiveTypeTree;
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCTypeParameter;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Pair;

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.util.Elements;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.IntStream;

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
            private Stack<Symbol> currentMember = new Stack<>();

            private Symbol currentMember() { return currentMember.peek(); }

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
                if (variable.sym == null) {
                    super.visitVarDef(variable);
                } else {
                    this.currentMember.push(variable.sym);
                    super.visitVarDef(variable);
                    this.currentMember.pop();
                }
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
                        ClassSymbol targetSymbol = resolveImport(identifier.getName());
                        if (targetSymbol != null) {
                            addOwner(targetSymbol, method.sym);
                        }
                    }
                }
                this.currentMember.push(method.sym);
                super.visitMethodDef(method);
                this.currentMember.pop();
            }

            @Override public void visitNewClass(JCNewClass tree) {
                if (tree.getIdentifier() instanceof JCFieldAccess) {
                    JCFieldAccess identifier = (JCFieldAccess) tree.getIdentifier();
                    addName(((JCIdent) identifier.selected).name.toString(), currentMember());
                } else if (tree.getIdentifier() instanceof JCIdent) {
                    JCIdent identifier = (JCIdent) tree.getIdentifier();
                    ClassSymbol targetSymbol = resolveImport(identifier.getName());
                    if (targetSymbol != null) {
                        addOwner(targetSymbol, currentMember());
                    }
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
                        ClassSymbol targetSymbol = resolveImport(identifier.name);
                        JCMethodDecl method = findMethod(targetSymbol, fieldAccess.name, methodInvocation.getArguments());
                        if (method != null && method.getReturnType() instanceof JCIdent) {
                            JCIdent returnType = (JCIdent) method.getReturnType();
                            PackageElement packageElement = elements.getPackageOf(returnType.sym);
                            addName(packageElement.getQualifiedName().toString(), currentMember());
                        }
                    } else if (fieldAccess.selected instanceof JCIdent) {
                        JCIdent identifier = (JCIdent) fieldAccess.selected;
                        ClassSymbol targetSymbol = resolveImport(identifier.name);
                        JCMethodDecl method = findMethod(targetSymbol, fieldAccess.name, methodInvocation.getArguments());
                        if (method != null) {
                            PackageElement packageElement = elements.getPackageOf(method.sym);
                            addName(packageElement.getQualifiedName().toString(), currentMember());
                        }
                    }
                }
                super.visitApply(methodInvocation);
            }

            private JCMethodDecl findMethod(ClassSymbol typeSymbol, Name methodName, List<JCExpression> arguments) {
                JCClassDecl targetClass = (typeSymbol == null) ? null : (JCClassDecl) elements.getTree(typeSymbol);
                if (targetClass == null)
                    return null;
                for (JCTree member : targetClass.getMembers()) {
                    if (member instanceof JCMethodDecl) {
                        JCMethodDecl method = (JCMethodDecl) member;
                        if (method.name.contentEquals(methodName) && argMatch(method.getParameters(), arguments)) {
                            return method;
                        }
                    }
                }
                return null;
            }

            private boolean argMatch(List<JCVariableDecl> expecteds, List<JCExpression> actuals) {
                if (expecteds.size() != actuals.size())
                    return false;
                return IntStream.range(0, expecteds.size()).allMatch(i -> argMatch(expecteds.get(i).vartype, actuals.get(i)));
            }

            private boolean argMatch(JCExpression expected, JCExpression actual) {
                return (
                    expected instanceof JCPrimitiveTypeTree && actual instanceof JCLiteral
                        && ((JCPrimitiveTypeTree) expected).typetag == ((JCLiteral) actual).typetag
                ) || (
                    expected instanceof JCIdent && actual instanceof JCLiteral
                        && ((JCIdent) expected).type.toString().equals(((JCLiteral) actual).value.getClass().getName())
                ) || (
                    expected instanceof JCIdent && actual instanceof JCNewClass
                        && ((JCIdent) expected).type.toString().equals(resolveImport(((JCIdent) ((JCNewClass) actual).getIdentifier()).name).className())
                );
            }

            private ClassSymbol resolveImport(Name name) {
                return compilationUnit.getImports().stream()
                    .filter(i -> ((JCFieldAccess) i.getQualifiedIdentifier()).name.contentEquals(name))
                    .map(i -> (ClassSymbol) ((JCFieldAccess) i.getQualifiedIdentifier()).sym)
                    .findAny().orElse(null);
            }
        });
        dependencies.remove(classSymbol.packge().name.toString());
        dependencies.remove("java.lang");
        extraImports.removeAll(dependencies.keySet());
    }
}

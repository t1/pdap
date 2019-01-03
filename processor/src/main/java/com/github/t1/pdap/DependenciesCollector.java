package com.github.t1.pdap;

import com.sun.tools.javac.code.Attribute.Compound;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.ArrayType;
import com.sun.tools.javac.code.Type.ClassType;
import com.sun.tools.javac.code.Type.TypeVar;
import com.sun.tools.javac.code.Type.WildcardType;
import com.sun.tools.javac.model.FilteredMemberList;
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
import com.sun.tools.javac.tree.JCTree.JCTypeApply;
import com.sun.tools.javac.tree.JCTree.JCTypeCast;
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
                } else if (variable.getType() instanceof JCTypeApply) { // external type
                    JCTypeApply typeApply = (JCTypeApply) variable.getType();
                    addOwner(typeApply.type.tsym, variable.sym);
                    for (Type typeParameter : typeApply.type.getTypeArguments()) {
                        if (typeParameter instanceof ClassType)
                            addOwner(((ClassType) typeParameter).tsym, variable.sym);
                        if (typeParameter instanceof WildcardType) {
                            if (typeParameter.isExtendsBound() && ((WildcardType) typeParameter).getExtendsBound() != null)
                                addOwner(((WildcardType) typeParameter).getExtendsBound().tsym, variable.sym);
                            if (typeParameter.isSuperBound() && ((WildcardType) typeParameter).getSuperBound() != null)
                                addOwner(((WildcardType) typeParameter).getSuperBound().tsym, variable.sym);
                        }
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
                        ClassSymbol targetSymbol = resolve(identifier.getName());
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
                    ClassSymbol targetSymbol = resolve(identifier.getName());
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
                        ClassSymbol targetSymbol = resolve(identifier.name);
                        MethodSymbol method = findMethod(targetSymbol, fieldAccess.name, methodInvocation.getArguments());
                        if (method != null && method.getReturnType() != null) {
                            PackageElement packageElement = elements.getPackageOf(method.getReturnType().tsym);
                            addName(packageElement.getQualifiedName().toString(), currentMember());
                        }
                    } else if (fieldAccess.selected instanceof JCIdent) {
                        JCIdent identifier = (JCIdent) fieldAccess.selected;
                        ClassSymbol targetSymbol = resolve(identifier.name);
                        addMethodOwner(methodInvocation, fieldAccess, targetSymbol);
                    } else if (fieldAccess.selected instanceof JCFieldAccess) {
                        JCFieldAccess selected = (JCFieldAccess) fieldAccess.selected;
                        ClassSymbol targetElement = elements.getTypeElement(selected.toString());
                        addMethodOwner(methodInvocation, fieldAccess, targetElement);
                    }
                }
                super.visitApply(methodInvocation);
            }

            private void addMethodOwner(JCMethodInvocation methodInvocation, JCFieldAccess fieldAccess, ClassSymbol targetSymbol) {
                MethodSymbol method = findMethod(targetSymbol, fieldAccess.name, methodInvocation.getArguments());
                if (method != null) {
                    PackageElement packageElement = elements.getPackageOf(method);
                    addName(packageElement.getQualifiedName().toString(), currentMember());
                }
            }

            private MethodSymbol findMethod(ClassSymbol typeSymbol, Name methodName, List<JCExpression> arguments) {
                FilteredMemberList members = elements.getAllMembers(typeSymbol);
                for (Symbol member : members) {
                    if (member instanceof MethodSymbol) {
                        MethodSymbol method = (MethodSymbol) member;
                        if (method.name.contentEquals(methodName) && argMatch(arguments, method.getParameters(), method.isVarArgs())) {
                            return method;
                        }
                    }
                }
                return null;
            }

            private boolean argMatch(List<JCExpression> actuals, List<VarSymbol> expecteds, boolean varArgs) {
                if (expecteds.size() == actuals.size() || varArgs && actuals.size() >= expecteds.size() - 1) {
                    return IntStream.range(0, actuals.size()).allMatch(i -> {
                        boolean isLastExpected = i >= expecteds.size() - 1;
                        int expectedIndex = isLastExpected ? expecteds.size() - 1 : i;
                        return argMatch(expecteds.get(expectedIndex).type, actuals.get(i), varArgs && isLastExpected);
                    });
                } else {
                    return false;
                }
            }

            private boolean argMatch(Type expected, JCExpression actual, boolean varArgs) {
                if (varArgs)
                    expected = ((ArrayType) expected).elemtype;
                if (expected instanceof TypeVar)
                    expected = ((TypeVar) expected).bound;
                String expectedString = expected.isPrimitive() ? boxed(expected).getName() : expected.asElement().toString();
                String actualString = (actual instanceof JCLiteral) ? ((JCLiteral) actual).value.getClass().getName()
                    : (actual instanceof JCTypeCast) ? resolve(((JCIdent) ((JCTypeCast) actual).getType()).name).className()
                    : resolve(((JCIdent) ((JCNewClass) actual).getIdentifier()).name).className();
                return expectedString.equals(actualString);
            }

            private Class<?> boxed(Type type) {
                switch (type.getTag()) {
                    case BYTE:
                        return Byte.class;
                    case CHAR:
                        return Character.class;
                    case SHORT:
                        return Short.class;
                    case LONG:
                        return Long.class;
                    case FLOAT:
                        return Float.class;
                    case INT:
                        return Integer.class;
                    case DOUBLE:
                        return Double.class;
                    case BOOLEAN:
                        return Boolean.class;
                    case VOID:
                        return Void.class;
                }
                throw new UnsupportedOperationException("unboxable type " + type);
            }

            private ClassSymbol resolve(Name name) {
                return compilationUnit.getImports().stream()
                    .filter(i -> ((JCFieldAccess) i.getQualifiedIdentifier()).name.contentEquals(name))
                    .map(i -> (ClassSymbol) ((JCFieldAccess) i.getQualifiedIdentifier()).sym)
                    .findAny().orElseGet(() -> {
                        ClassSymbol typeElement = elements.getTypeElement(name);
                        if (typeElement == null)
                            typeElement = elements.getTypeElement("java.lang." + name);
                        return typeElement;
                    });
            }
        });
        dependencies.remove(classSymbol.packge().name.toString());
        dependencies.remove("java.lang");
        extraImports.removeAll(dependencies.keySet());
    }
}

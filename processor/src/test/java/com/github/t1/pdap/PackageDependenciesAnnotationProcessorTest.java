package com.github.t1.pdap;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;

class PackageDependenciesAnnotationProcessorTest extends AbstractAnnotationProcessorTest {
    private void compileSource(String source) {
        compile(
            packageInfo("source", "target"),
            file("source/Source", source),

            packageInfo("target"),
            targetInterface());
    }

    private void compileForbiddenSource(String source) {
        compile(
            packageInfo("source"),
            file("source/Source", source),

            packageInfo("target"),
            targetInterface());
    }

    private StringJavaFileObject packageInfo(String packageName, String... dependencies) {
        return file(packageName.replace('.', '/') + "/package-info", "" +
            "@DependsOn(" + dependenciesString(dependencies) + ")\n" +
            "package " + packageName + ";\n" +
            "\n" +
            "import com.github.t1.pdap.DependsOn;\n");
    }

    private String dependenciesString(String... dependencies) {
        switch (dependencies.length) {
            case 0:
                return "";
            case 1:
                return "\"" + dependencies[0] + "\"";
            default:
                return Stream.of(dependencies).collect(joining("\", \"", "{\"", "\"}"));
        }
    }

    private StringJavaFileObject targetInterface() {
        return file("target/Target", "" +
            "package target;\n" +
            "\n" +
            "public interface Target {\n" +
            "}\n");
    }

    private StringJavaFileObject targetClass() {
        return file("target/Target", "" +
            "package target;\n" +
            "\n" +
            "public class Target {\n" +
            "}\n");
    }

    private StringJavaFileObject targetAnnotation() {
        return file("target/Target", "" +
            "package target;\n" +
            "\n" +
            "public @interface Target {\n" +
            "    String value();\n" +
            "}\n");
    }

    private StringJavaFileObject targetEnum() {
        return file("target/Target", "" +
            "package target;\n" +
            "\n" +
            "public enum Target {\n" +
            "    FOO\n" +
            "}\n");
    }


    @Nested class BasicCompilerTests {
        @Test void shouldSimplyCompile() {
            compile(file("Simple", "" +
                "public class Simple {\n" +
                "}"));

            expect();
        }

        @Test void shouldReportErrorForUnknownSymbol() {
            compile(file("Failing", "" +
                "@UnknownAnnotation\n" +
                "public class Failing {\n" +
                "}"));

            expect(
                warning("compiler.warn.proc.annotations.without.processors", "No processor claimed any of these annotations: UnknownAnnotation"),
                error("/Failing.java", 1, 1, 18, 1, 2,
                    "compiler.err.cant.resolve", "cannot find symbol\n  symbol: class UnknownAnnotation"));
        }
    }

    @Nested class DependsOnTests {
        @Test void shouldReportErrorForInvalidDependsOn() {
            compile(
                packageInfo("source", "undefined"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "}\n"));

            expect(
                error("/source/package-info.java", 0, 0, 77, 1, 1,
                    "compiler.err.proc.messager", "Invalid @DependsOn: unknown package [undefined]")
            );
        }

        @Test void shouldReportErrorForInvalidSuperDependsOn() {
            compile(
                packageInfo("source", "undefined"),
                packageInfo("source.sub"),
                file("source/sub/Source", "" +
                    "package source.sub;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "}\n"));

            expect(
                error("/source/package-info.java", 0, 0, 77, 1, 1,
                    "compiler.err.proc.messager", "Invalid @DependsOn: unknown package [undefined]")
            );
        }

        @Test void shouldWarnAboutMissingDependsOn() {
            compile(
                file("source/package-info", "" +
                    "package source;"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target target;\n" +
                    "}\n"),

                packageInfo("target"),
                targetInterface());

            expect(
                warning("/source/package-info.java", 0, 0, 15, 1, 1,
                    "compiler.warn.proc.messager", "no @DependsOn annotation")
            );
        }

        @Test void shouldNotWarnAboutMissingSuperDependsOn() {
            compile(
                packageInfo("source.sub", "target"),
                file("source/sub/Source", "" +
                    "package source.sub;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target target;\n" +
                    "}\n"),

                packageInfo("target"),
                targetInterface());

            expect();
        }

        @Test void shouldNotWarnAboutMissingSuperPackageInfo() {
            compile(
                packageInfo("source.sub1.sub2", "target"),
                file("source/sub1/sub2/Source", "" +
                    "package source.sub1.sub2;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target target;\n" +
                    "}\n"),

                packageInfo("target"),
                targetClass());

            expect();
        }

        @Test void shouldReportErrorForDependencyOnSelf() {
            compile(
                packageInfo("source", "source", "target"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "   private Target target;" +
                    "}\n"),

                packageInfo("target"),
                targetInterface());

            expect(
                error("/source/package-info.java", 0, 0, 86, 1, 1,
                    "compiler.err.proc.messager", "Cyclic dependency declared on [source]")
            );
        }

        @Test void shouldWarnAboutUnusedDependency() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "}\n");

            expect(
                warning("/source/package-info.java", 0, 0, 74, 1, 1,
                    "compiler.warn.proc.messager", "Unused dependency on [target]")
            );
        }
    }

    @Nested class ImportedDependencies {
        @Test void shouldNotReportErrorForFieldWithAllowedDependency() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target target;\n" +
                "}\n");

            expect();
        }

        @Test void shouldReportErrorForFieldWithForbiddenDependency() {
            compileForbiddenSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target target;\n" +
                "}\n");

            expect(
                error("/source/Source.java", 81, 66, 88, 6, 20,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorForEnumFieldWithForbiddenDependency() {
            compile(
                packageInfo("source"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target target;\n" +
                    "}\n"),

                packageInfo("target"),
                targetEnum());

            expect(
                error("/source/Source.java", 81, 66, 88, 6, 20,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorForFieldWithForbiddenDependencyOnNonCompiledClass() {
            compileForbiddenSource("" +
                "package source;\n" +
                "\n" +
                "import java.util.List;\n" +
                "\n" +
                "public class Source {\n" +
                "    private List<?> list;\n" +
                "}\n");

            expect(
                error("/source/Source.java", 81, 66, 88, 6, 20,
                    "compiler.err.proc.messager", "Forbidden dependency on [java.util]")
            );
        }

        @Test void shouldReportErrorForFieldArgWithForbiddenDependencyOnNonCompiledClass() {
            compile(
                packageInfo("source", "target"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Enum<Target> list;\n" +
                    "}\n"),

                packageInfo("target"),
                targetEnum());

            expect(
                error("/source/Source.java", 81, 66, 88, 6, 20,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorForNonCompiledFieldArgWithForbiddenDependencyOnNonCompiledClass() {
            compile(
                packageInfo("source"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import java.util.List;\n" +
                    "import java.math.BigInteger;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private List<BigInteger> list;\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 81, 66, 88, 6, 20,
                    "compiler.err.proc.messager", "Forbidden dependency on [java.math]")
            );
        }

        @Test void shouldReportErrorForFieldValueWithForbiddenDependency() {
            compile(
                packageInfo("source"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Object target = new Target();\n" +
                    "}\n"),

                packageInfo("target"),
                targetClass());

            expect(
                error("/source/Source.java", 81, 66, 103, 6, 20,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldNotReportErrorAboutForbiddenFieldAnnotation() {
            compile(
                packageInfo("source"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private @Target(\"t\") String target;\n" +
                    "}\n"),

                packageInfo("target"),
                targetAnnotation());

            expect(
                warning("compiler.warn.proc.annotations.without.processors", "No processor claimed any of these annotations: target.Target")
            );
        }

        @Test void shouldReportErrorAboutForbiddenMethodInvocationWithoutArguments() {
            compile(
                packageInfo("source"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() {\n" +
                    "        new Target().bar();\n" +
                    "    }\n" +
                    "}\n"),

                packageInfo("target"),
                file("target/Target", "" +
                    "package target;\n" +
                    "\n" +
                    "public class Target {\n" +
                    "    public void bar() {}\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 79, 66, 120, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenMethodInvocationWithArgument() {
            compile(
                packageInfo("source"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() {\n" +
                    "        new Target().bar(1);\n" +
                    "    }\n" +
                    "}\n"),

                packageInfo("target"),
                file("target/Target", "" +
                    "package target;\n" +
                    "\n" +
                    "public class Target {\n" +
                    "    public void bar(int i) {}\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 79, 66, 121, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenOverloadedMethodInvocation() {
            compile(
                packageInfo("source"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() {\n" +
                    "        new Target().bar(1);\n" +
                    "    }\n" +
                    "}\n"),

                packageInfo("target"),
                file("target/Target", "" +
                    "package target;\n" +
                    "\n" +
                    "public class Target {\n" +
                    "    public void bar(String s) {}\n" +
                    "    public void bar(int i) {}\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 79, 66, 121, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenStaticMethodInvocation() {
            compile(
                packageInfo("source"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() {\n" +
                    "        Target.bar();\n" +
                    "    }\n" +
                    "}\n"),

                packageInfo("target"),
                file("target/Target", "" +
                    "package target;\n" +
                    "\n" +
                    "public class Target {\n" +
                    "    public static void bar() {}\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 79, 66, 114, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenStaticMethodInvocationToNonCompiledClass() {
            compile(
                packageInfo("source"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import java.util.Arrays;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() {" +
                    "        Arrays.asList(\"bar\");\n" +
                    "    }\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 47, 40, 104, 5, 8,
                    "compiler.err.proc.messager", "Forbidden dependency on [java.util]")
            );
        }

        @Test void shouldNotReportErrorAboutAllowedAnonymousSubclassInMethodBody() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private void foo() { Object target = new Target() {}; }\n" +
                "}\n");

            expect();
        }

        @Test void shouldReportErrorAboutForbiddenAnonymousSubclassInMethodBody() {
            compileForbiddenSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private void foo() { Object target = new Target() {}; }\n" +
                "}\n");

            expect(
                error("/source/Source.java", 79, 66, 121, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldNotReportErrorAboutAllowedMethodReturnType() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;" +
                "\n" +
                "public class Source {\n" +
                "    private Target foo() { return null; }\n" +
                "}\n");

            expect();
        }

        @Test void shouldReportErrorAboutForbiddenMethodReturnType() {
            compileForbiddenSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;" +
                "\n" +
                "public class Source {\n" +
                "    private Target foo() { return null; }\n" +
                "}\n");

            expect(
                error("/source/Source.java", 80, 65, 102, 5, 20,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldNotReportErrorForEnumWithAllowedDependency() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "enum Source {\n" +
                "    FOO;\n" +
                "    private Target target;\n" +
                "}\n");

            expect();
        }

        @Test void shouldReportErrorForEnumWithForbiddenDependency() {
            compileForbiddenSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "enum Source {\n" +
                "    FOO;\n" +
                "    private Target target;\n" +
                "}\n");

            expect(
                error("/source/Source.java", 82, 67, 89, 7, 20,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldNotReportErrorForAnnotationWithAllowedDependency() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public @interface Source {\n" +
                "    Class<Target> value();\n" +
                "}\n");

            expect();
        }

        @Test void shouldReportErrorForAnnotationWithForbiddenDependency() {
            compileForbiddenSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public @interface Source {\n" +
                "    Class<Target> value();\n" +
                "}\n");

            expect(
                error("/source/Source.java", 85, 71, 93, 6, 19,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldNotReportErrorForInterfaceWithAllowedDependency() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public interface Source extends Target {\n" +
                "}\n");

            expect();
        }

        @Test void shouldReportErrorForInterfaceWithForbiddenDependency() {
            compileForbiddenSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public interface Source extends Target {\n" +
                "}\n");

            expect(
                error("/source/Source.java", 47, 40, 82, 5, 8,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldWarnAboutClassWithUnusedImport() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source{\n" +
                "}\n");

            expect(
                warning("/source/Source.java", 47, 40, 62, 5, 8,
                    "compiler.warn.proc.messager", "Import [target] not found as dependency"),
                warning("/source/package-info.java", 0, 0, 74, 1, 1,
                    "compiler.warn.proc.messager", "Unused dependency on [target]")
            );
        }

        @Test void shouldReportErrorForForbiddenExtendsClassDependency() {
            compile(
                packageInfo("source"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source extends Target {\n" +
                    "}\n"),

                packageInfo("target"),
                targetClass());

            expect(
                error("/source/Source.java", 47, 40, 78, 5, 8,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorForForbiddenExtendsInterfaceDependency() {
            compile(
                packageInfo("source"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public interface Source extends Target {\n" +
                    "}\n"),

                packageInfo("target"),
                targetInterface());

            expect(
                error("/source/Source.java", 47, 40, 82, 5, 8,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorForForbiddenImplementsDependency() {
            compile(
                packageInfo("source"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source implements Target {\n" +
                    "}\n"),

                packageInfo("target"),
                targetInterface());

            expect(
                error("/source/Source.java", 47, 40, 81, 5, 8,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldReportErrorsForTwoForbiddenAndOneAllowedFieldDependency() {
            compile(
                packageInfo("source", "target3"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target1.Target1a;\n" +
                    "import target1.Target1b;\n" +
                    "import target2.Target2;\n" +
                    "import target3.Target3;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target1a t1a;\n" +
                    "    private Target1b t1b;\n" +
                    "    private Target2 t2;\n" +
                    "    private Target3 t3;\n" +
                    "}\n"),

                packageInfo("target1"),
                file("target1/Target1a", "" +
                    "package target1;\n" +
                    "\n" +
                    "public class Target1a {\n" +
                    "}\n"),
                file("target1/Target1b", "" +
                    "package target1;\n" +
                    "\n" +
                    "public class Target1b {\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2", "" +
                    "package target2;\n" +
                    "\n" +
                    "public class Target2 {\n" +
                    "}\n"),

                packageInfo("target3"),
                file("target3/Target3", "" +
                    "package target3;\n" +
                    "\n" +
                    "public class Target3 {\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 159, 142, 163, 9, 22,
                    "compiler.err.proc.messager", "Forbidden dependency on [target1]"),
                error("/source/Source.java", 210, 194, 213, 11, 21,
                    "compiler.err.proc.messager", "Forbidden dependency on [target2]")
            );
        }

        @Test void shouldReportErrorForForbiddenTypeBoundDependency() {
            compile(
                packageInfo("source"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source<T extends Target> {\n" +
                    "}\n"),

                packageInfo("target"),
                targetClass());

            expect(
                error("/source/Source.java", 47, 40, 81, 5, 8,
                    "compiler.err.proc.messager", "Forbidden dependency on [target]")
            );
        }

        @Test void shouldNotReportErrorForDependencyToAnnotation() {
            compile(
                packageInfo("source"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "@Target(\"/source\")" +
                    "public class Source {\n" +
                    "}\n"),

                packageInfo("target"),
                targetAnnotation());

            expect(
                warning("compiler.warn.proc.annotations.without.processors", "No processor claimed any of these annotations: target.Target")
            );
        }
    }

    @Nested class QualifiedDependencies {
        @Test void shouldNotReportErrorAboutAllowedQualifiedFieldType() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "    private target.Target target;\n" +
                "}\n");

            expect();
        }

        @Test void shouldNotReportErrorAboutAllowedQualifiedMethodReturnType() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "    private target.Target foo() { return null; }\n" +
                "}\n");

            expect();
        }

        @Test void shouldNotReportErrorAboutAllowedQualifiedVariableInMethodBody() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "    private void foo() { target.Target target = null; }\n" +
                "}\n");

            expect();
        }

        @Test void shouldNotReportErrorAboutAllowedQualifiedAnonymousSubclassInMethodBody() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "    private void foo() { Object target = new target.Target() {}; }\n" +
                "}\n");

            expect();
        }

        @Test void shouldNotReportErrorAboutAllowedQualifiedAnonymousSubclassField() {
            compileSource("" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Object target = new target.Target() {};\n" +
                "}\n");

            expect();
        }

        @Test void shouldReportErrorAboutForbiddenQualifiedStaticMethodInvocationToNonCompiledClass() {
            compile(
                packageInfo("source"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() {" +
                    "        java.util.Arrays.asList(\"bar\");\n" +
                    "    }\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 47, 40, 104, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [java.util]")
            );
        }
    }

    @Nested class DependsOnInheritance {
        @Test void shouldNotReportErrorAboutSuperPackageAllowingDependencyWithoutPackageDependsOn() {
            compile(
                packageInfo("source", "target"),
                file("source/sub/Source", "" +
                    "package source.sub;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target target;\n" +
                    "}\n"),

                packageInfo("target"),
                targetInterface());

            expect();
        }

        @Test void shouldNotReportErrorAboutSuperPackageAllowingDependencyWithEmptyPackageDependsOn() {
            compile(
                packageInfo("source", "target"),
                packageInfo("source.sub"),
                file("source/sub/Source", "" +
                    "package source.sub;\n" +
                    "\n" +
                    "import target.Target;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target target;\n" +
                    "}\n"),

                packageInfo("target"),
                targetInterface());

            expect();
        }

        @Test void shouldNotReportErrorAboutSuperPackageAllowingDependencyWithPackageDependsOnMerge() {
            compile(
                packageInfo("source", "target1"),
                packageInfo("source.sub", "target2"),
                file("source/sub/Source", "" +
                    "package source.sub;\n" +
                    "\n" +
                    "import target1.Target1;\n" +
                    "import target2.Target2;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target1 target1;\n" +
                    "    private Target2 target2;\n" +
                    "}\n"),

                packageInfo("target1"),
                file("target1/Target1", "" +
                    "package target1;\n" +
                    "\n" +
                    "public interface Target1 {\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2", "" +
                    "package target2;\n" +
                    "\n" +
                    "public interface Target2 {\n" +
                    "}\n"));

            expect();
        }

        @Test void shouldNotReportErrorAboutUnusedSuperPackageDependency() {
            compile(
                packageInfo("source", "target1"),
                packageInfo("source.sub", "target2"),
                file("source/sub/Source", "" +
                    "package source.sub;\n" +
                    "\n" +
                    "import target2.Target2;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private Target2 target2;\n" +
                    "}\n"),

                packageInfo("target1"),
                file("target1/Target1", "" +
                    "package target1;\n" +
                    "\n" +
                    "public interface Target1 {\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2", "" +
                    "package target2;\n" +
                    "\n" +
                    "public interface Target2 {\n" +
                    "}\n"));

            expect();
        }
    }

    @Nested class IndirectDependencies {
        @Test void shouldNotReportErrorAboutAllowedIndirectDependency() {
            compile(
                packageInfo("source", "target1", "target2"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target1.Target1;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() { Object target2 = new Target1().target2(); }\n" +
                    "}\n"),

                packageInfo("target1", "target2"),
                file("target1/Target1", "" +
                    "package target1;\n" +
                    "\n" +
                    "import target2.Target2;\n" +
                    "\n" +
                    "public class Target1 {\n" +
                    "    public Target2 target2() { return null; }\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2", "" +
                    "package target2;\n" +
                    "\n" +
                    "public class Target2 {\n" +
                    "}\n"));

            expect();
        }

        @Test void shouldReportErrorAboutForbiddenIndirectDependency() {
            compile(
                packageInfo("source", "target1"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target1.Target1;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() { Object target2 = new Target1().target2(); }\n" +
                    "}\n"),

                packageInfo("target1", "target2"),
                file("target1/Target1", "" +
                    "package target1;\n" +
                    "\n" +
                    "import target2.Target2;\n" +
                    "\n" +
                    "public class Target1 {\n" +
                    "    public Target2 target2() { return null; }\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2", "" +
                    "package target2;\n" +
                    "\n" +
                    "public class Target2 {\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 81, 68, 132, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target2]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenIndirectDependencyWithOverloadedPrimitiveMethods() {
            compile(
                packageInfo("source", "target1"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target1.Target1;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() { Object target2 = new Target1().target2(1); }\n" +
                    "}\n"),

                packageInfo("target1", "target2"),
                file("target1/Target1", "" +
                    "package target1;\n" +
                    "\n" +
                    "import target2.Target2;\n" +
                    "\n" +
                    "public class Target1 {\n" +
                    "    public String target2(String s) { return null; }\n" +
                    "    public Target2 target2(int i) { return null; }\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2", "" +
                    "package target2;\n" +
                    "\n" +
                    "public class Target2 {\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 81, 68, 133, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target2]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenIndirectDependencyWithOverloadedNonPrimitiveLiteralMethods() {
            compile(
                packageInfo("source", "target1"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target1.Target1;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() { Object target2 = new Target1().target2(\"\"); }\n" +
                    "}\n"),

                packageInfo("target1", "target2", "java.math"),
                file("target1/Target1", "" +
                    "package target1;\n" +
                    "\n" +
                    "import target2.Target2;\n" +
                    "import java.math.BigInteger;\n" +
                    "\n" +
                    "public class Target1 {\n" +
                    "    public String target2(BigInteger i) { return null; }\n" +
                    "    public Target2 target2(String s) { return null; }\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2", "" +
                    "package target2;\n" +
                    "\n" +
                    "public class Target2 {\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 81, 68, 134, 6, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target2]")
            );
        }

        @Test void shouldReportErrorAboutForbiddenIndirectDependencyWithOverloadedNonPrimitiveValueMethods() {
            compile(
                packageInfo("source", "target1", "java.math"),
                file("source/Source", "" +
                    "package source;\n" +
                    "\n" +
                    "import target1.Target1;\n" +
                    "import java.math.BigInteger;\n" +
                    "\n" +
                    "public class Source {\n" +
                    "    private void foo() { Object target2 = new Target1().target2(new BigInteger(\"123\")); }\n" +
                    "}\n"),

                packageInfo("target1", "target2", "java.math"),
                file("target1/Target1", "" +
                    "package target1;\n" +
                    "\n" +
                    "import target2.Target2;\n" +
                    "import java.math.BigInteger;\n" +
                    "\n" +
                    "public class Target1 {\n" +
                    "    public String target2(String s) { return null; }\n" +
                    "    public Target2 target2(BigInteger i) { return null; }\n" +
                    "}\n"),

                packageInfo("target2"),
                file("target2/Target2", "" +
                    "package target2;\n" +
                    "\n" +
                    "public class Target2 {\n" +
                    "}\n"));

            expect(
                error("/source/Source.java", 110, 97, 182, 7, 18,
                    "compiler.err.proc.messager", "Forbidden dependency on [target2]")
            );
        }
    }
}

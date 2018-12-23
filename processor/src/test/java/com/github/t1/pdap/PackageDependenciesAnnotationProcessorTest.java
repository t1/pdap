package com.github.t1.pdap;

import org.junit.jupiter.api.Test;

class PackageDependenciesAnnotationProcessorTest extends AbstractAnnotationProcessorTest {
    private void compileSource(String source) {
        compile(
            "source/package-info", "" +
                "@DependsUpon(\"target\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsUpon;\n",
            "source/Source", "" +
                source,
            "target/package-info", "" +
                "@DependsUpon()\n" +
                "package target;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsUpon;\n",
            "target/Target", "" +
                "package target;\n" +
                "\n" +
                "public interface Target {\n" +
                "}\n");
    }

    @Test void shouldSucceedWithoutAnnotations() {
        compile("SucceedAnnotationProcessing", "" +
            "public class SucceedAnnotationProcessing {\n" +
            "}");

        expect();
    }

    @Test void shouldFailToCompileWithUnknownSymbol() {
        compile(
            "FailAnnotationProcessing", "" +
                "@UnknownAnnotation\n" +
                "public class FailAnnotationProcessing {\n" +
                "}");

        expect(
            warning("compiler.warn.proc.annotations.without.processors", "No processor claimed any of these annotations: UnknownAnnotation"),
            error("/FailAnnotationProcessing.java", 1, 1, 18, 1, 2, "compiler.err.cant.resolve", "cannot find symbol\n  symbol: class UnknownAnnotation"));
    }

    @Test void shouldFailInvalidDependsUpon() {
        compile(
            "source/package-info", "" +
                "@DependsUpon(\"undefined\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsUpon;\n",
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "}\n");

        expect(
            error("Invalid `DependsOn`: Unknown package [undefined].")
        );
    }

    @Test void shouldCompileClassWithoutDependsOn() {
        compile(
            "source/package-info", "" +
                "package source;",
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target target;\n" +
                "}\n",
            "target/package-info", "" +
                "@DependsUpon()\n" +
                "package target;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsUpon;\n",
            "target/Target", "" +
                "package target;\n" +
                "\n" +
                "public class Target {\n" +
                "}\n");

        expect(
            note("no @DependsUpon annotation on [source.Source]")
        );
    }

    @Test void shouldFailToCompileDependencyOnSelf() {
        compile(
            "source/package-info", "" +
                "@DependsUpon(\"source\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsUpon;\n",
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "}\n");

        expect(
            error("Cyclic dependency declared: [source] -> [source]")
        );
    }

    @Test void shouldWarnAboutUnusedDependency() {
        compileSource("package source;\n" +
            "\n" +
            "public class Source {\n" +
            "" +
            "}\n");

        expect(
            warning("Unused dependency [source] -> [target]")
        );
    }

    @Test void shouldCompileClassWithAllowedDependency() {
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

    @Test void shouldCompileEnumWithAllowedDependency() {
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

    @Test void shouldCompileAnnotationWithAllowedDependency() {
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

    @Test void shouldCompileInterfaceWithAllowedDependency() {
        compileSource("" +
            "package source;\n" +
            "\n" +
            "import target.Target;\n" +
            "\n" +
            "public interface Source extends Target {\n" +
            "}\n");

        expect();
    }

    @Test void shouldFailWithTwoDisallowedDependencies() {
        compile(
            "source/package-info", "" +
                "@DependsUpon(\"target3\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsUpon;\n",
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "import target1.Target1a;\n" +
                "import target1.Target1b;\n" +
                "import target2.Target2;\n" +
                "import target3.Target3;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target1a target1a;\n" +
                "    private Target1b target1b;\n" +
                "    private Target2 target2;\n" +
                "    private Target3 target3;\n" +
                "}\n",
            "target1/Target1a", "" +
                "package target1;\n" +
                "\n" +
                "public class Target1a {\n" +
                "}\n",
            "target1/Target1b", "" +
                "package target1;\n" +
                "\n" +
                "public class Target1b {\n" +
                "}\n",
            "target2/Target2", "" +
                "package target2;\n" +
                "\n" +
                "public class Target2 {\n" +
                "}\n",
            "target3/Target3", "" +
                "package target3;\n" +
                "\n" +
                "public class Target3 {\n" +
                "}\n");

        expect(
            error("Forbidden dependency [source] -> [target1]"),
            error("Forbidden dependency [source] -> [target2]")
        );
    }

    @Test void shouldCompileClassWithAllowedQualifiedDependencyField() {
        compileSource("package source;\n" +
            "\n" +
            "public class Source {\n" +
            "    private target.Target target;\n" +
            "}\n");

        expect();
    }

    @Test void shouldCompileClassWithAllowedQualifiedDependencyMethodReturnType() {
        compileSource("package source;\n" +
            "\n" +
            "public class Source {\n" +
            "    private target.Target foo() { return null; }\n" +
            "}\n");

        expect();
    }

    @Test void shouldCompileClassWithAllowedQualifiedDependencyVarInMethodBody() {
        compileSource("package source;\n" +
            "\n" +
            "public class Source {\n" +
            "    private void foo() { target.Target target = null; }\n" +
            "}\n");

        expect();
    }

    @Test void shouldCompileClassWithAllowedQualifiedDependencyAnonymousSubclassInMethodBody() {
        compileSource("package source;\n" +
            "\n" +
            "public class Source {\n" +
            "    private void foo() { Object target = new target.Target() {}; }\n" +
            "}\n");

        expect();
    }

    @Test void shouldCompileClassWithAllowedQualifiedDependencyAnonymousSubclassField() {
        compileSource("package source;\n" +
            "\n" +
            "public class Source {\n" +
            "    private Object target = new target.Target() {};\n" +
            "}\n");

        expect();
    }
}

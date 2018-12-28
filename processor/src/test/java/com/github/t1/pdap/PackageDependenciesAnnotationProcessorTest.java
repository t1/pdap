package com.github.t1.pdap;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class PackageDependenciesAnnotationProcessorTest extends AbstractAnnotationProcessorTest {
    private void compileSource(String source) {
        compile(
            "source/package-info", "" +
                "@DependsOn(\"target\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "source/Source", "" +
                source,
            "target/package-info", "" +
                "@DependsOn()\n" +
                "package target;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
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

    @Test void shouldFailInvalidDependsOn() {
        compile(
            "source/package-info", "" +
                "@DependsOn(\"undefined\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "}\n");

        expect(
            error("/source/package-info.java", 0, 0, 77, 1, 1, "compiler.err.proc.messager", "Invalid @DependsOn: unknown package [undefined]")
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
                "@DependsOn()\n" +
                "package target;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "target/Target", "" +
                "package target;\n" +
                "\n" +
                "public class Target {\n" +
                "}\n");

        expect(
            warning("/source/package-info.java", 0, 0, 15, 1, 1, "compiler.warn.proc.messager", "no @DependsOn annotation")
        );
    }

    @Test void shouldCompileClassWithoutSuperDependsOn() {
        compile(
            "source/sub/package-info", "" +
                "@DependsOn(\"target\")\n" +
                "package source.sub;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "source/sub/Source", "" +
                "package source.sub;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target target;\n" +
                "}\n",
            "target/package-info", "" +
                "@DependsOn()\n" +
                "package target;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "target/Target", "" +
                "package target;\n" +
                "\n" +
                "public class Target {\n" +
                "}\n");

        expect();
    }

    @Test void shouldCompileClassWithoutParentPackageInfos() {
        compile(
            "source/sub1/sub2/package-info", "" +
                "@DependsOn(\"target\")\n" +
                "package source.sub1.sub2;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "source/sub1/sub2/Source", "" +
                "package source.sub1.sub2;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target target;\n" +
                "}\n",
            "target/package-info", "" +
                "@DependsOn()\n" +
                "package target;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "target/Target", "" +
                "package target;\n" +
                "\n" +
                "public class Target {\n" +
                "}\n");

        expect();
    }

    @Test void shouldCompileClassWithInvalidSuperDependsOn() {
        compile(
            "source/package-info", "" +
                "@DependsOn(\"undefined\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "source/sub/package-info", "" +
                "@DependsOn()\n" +
                "package source.sub;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "source/sub/Source", "" +
                "package source.sub;\n" +
                "\n" +
                "public class Source {\n" +
                "}\n");

        expect(
            error("/source/package-info.java", 0, 0, 77, 1, 1, "compiler.err.proc.messager", "Invalid @DependsOn: unknown package [undefined]")
        );
    }

    @Test void shouldFailToCompileDependencyOnSelf() {
        compile(
            "source/package-info", "" +
                "@DependsOn({\"source\", \"target\"})\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "   private Target target;" +
                "}\n",
            "target/package-info", "" +
                "@DependsOn()\n" +
                "package target;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "target/Target", "" +
                "package target;\n" +
                "\n" +
                "public interface Target {\n" +
                "}\n");

        expect(
            error("/source/package-info.java", 0, 0, 86, 1, 1, "compiler.err.proc.messager", "Cyclic dependency declared on [source]")
        );
    }

    @Test void shouldWarnAboutUnusedDependency() {
        compileSource("package source;\n" +
            "\n" +
            "public class Source {\n" +
            "}\n");

        expect(
            warning("/source/package-info.java", 0, 0, 74, 1, 1, "compiler.warn.proc.messager", "Unused dependency on [target]")
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

    @Test void shouldFailWithTwoForbiddenDependencies() {
        compile(
            "source/package-info", "" +
                "@DependsOn(\"target3\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
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
            warning("no @DependsOn annotation"),
            warning("no @DependsOn annotation"),
            warning("no @DependsOn annotation")
            // error("/source/Source.java", 123, 116, 259, 8, 8, "compiler.err.proc.messager", "Forbidden dependency [source] -> [target1]"),
            // error("/source/Source.java", 123, 116, 259, 8, 8, "compiler.err.proc.messager", "Forbidden dependency [source] -> [target2]")
        );
    }

    @Test void shouldCompileWithDependencyAnnotation() {
        compile(
            "source/package-info", "" +
                "@DependsOn(\"target\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "@Target(\"/source\")" +
                "public class Source {\n" +
                "}\n",
            "target/package-info", "" +
                "@DependsOn()\n" +
                "package target;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "target/Target", "" +
                "package target;\n" +
                "\n" +
                "public @interface Target {\n" +
                "    String value();\n" +
                "}\n");

        expect(
            warning("compiler.warn.proc.annotations.without.processors", "No processor claimed any of these annotations: target.Target")
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

    @Test void shouldCompileWithSuperPackageAllowingDependencyWithoutPackageDependsOn() {
        compile(
            "source/package-info", "" +
                "@DependsOn(\"target\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "source/sub/Source", "" +
                "package source.sub;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target target;\n" +
                "}\n",
            "target/package-info", "" +
                "@DependsOn()\n" +
                "package target;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "target/Target", "" +
                "package target;\n" +
                "\n" +
                "public interface Target {\n" +
                "}\n");

        expect();
    }

    @Test void shouldCompileWithSuperPackageAllowingDependencyWithEmptyPackageDependsOn() {
        compile(
            "source/package-info", "" +
                "@DependsOn(\"target\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "source/sub/package-info", "" +
                "@DependsOn()\n" +
                "package source.sub;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "source/sub/Source", "" +
                "package source.sub;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target target;\n" +
                "}\n",
            "target/package-info", "" +
                "@DependsOn()\n" +
                "package target;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "target/Target", "" +
                "package target;\n" +
                "\n" +
                "public interface Target {\n" +
                "}\n");

        expect();
    }

    @Test void shouldCompileWithSuperPackageAllowingDependencyWithPackageDependsOnMerge() {
        compile(
            "source/package-info", "" +
                "@DependsOn(\"target1\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "source/sub/package-info", "" +
                "@DependsOn(\"target2\")\n" +
                "package source.sub;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "source/sub/Source", "" +
                "package source.sub;\n" +
                "\n" +
                "import target1.Target1;\n" +
                "import target2.Target2;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target1 target1;\n" +
                "    private Target2 target2;\n" +
                "}\n",
            "target1/package-info", "" +
                "@DependsOn()\n" +
                "package target1;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "target1/Target1", "" +
                "package target1;\n" +
                "\n" +
                "public interface Target1 {\n" +
                "}\n",
            "target2/package-info", "" +
                "@DependsOn()\n" +
                "package target2;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "target2/Target2", "" +
                "package target2;\n" +
                "\n" +
                "public interface Target2 {\n" +
                "}\n");

        expect();
    }

    @Test void shouldCompileWithUnusedSuperPackageDependency() {
        compile(
            "source/package-info", "" +
                "@DependsOn(\"target1\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "source/sub/package-info", "" +
                "@DependsOn(\"target2\")\n" +
                "package source.sub;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "source/sub/Source", "" +
                "package source.sub;\n" +
                "\n" +
                "import target2.Target2;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target2 target2;\n" +
                "}\n",
            "target1/package-info", "" +
                "@DependsOn()\n" +
                "package target1;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "target1/Target1", "" +
                "package target1;\n" +
                "\n" +
                "public interface Target1 {\n" +
                "}\n",
            "target2/package-info", "" +
                "@DependsOn()\n" +
                "package target2;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "target2/Target2", "" +
                "package target2;\n" +
                "\n" +
                "public interface Target2 {\n" +
                "}\n");

        expect();
    }

    @Disabled @Test void shouldCompileWithIndirectDependency() {
        compile(
            "source/package-info", "" +
                "@DependsOn({\"target1\", \"target2\"})\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "import target1.Target1;\n" +
                "\n" +
                "public class Source {\n" +
                "    private void foo() { Object target2 = new Target1().target2(); }\n" +
                "}\n",
            "target1/package-info", "" +
                "@DependsOn(\"target2\")\n" +
                "package target1;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "target1/Target1", "" +
                "package target1;\n" +
                "\n" +
                "import target2.Target2;\n" +
                "\n" +
                "public class Target1 {\n" +
                "    public Target2 target2() { return null; }\n" +
                "}\n",
            "target2/package-info", "" +
                "@DependsOn()\n" +
                "package target2;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsOn;\n",
            "target2/Target2", "" +
                "package target2;\n" +
                "\n" +
                "public class Target2 {\n" +
                "}\n");

        expect();
    }
}

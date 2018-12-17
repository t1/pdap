package com.github.t1.pdap;

import org.junit.jupiter.api.Test;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Set;

class PackageDependenciesAnnotationProcessorTest extends AbstractAnnotationProcessorTest {
    @Test void shouldSucceedWithoutAnnotations() {
        DependenciesScanner.mockClassDependencies = Set.of();

        compile("SucceedAnnotationProcessing", "" +
            "public class SucceedAnnotationProcessing {\n" +
            "}");

        expect();
    }

    @Test void shouldFailToCompileWithUnknownSymbol() {
        DependenciesScanner.mockClassDependencies = Set.of();

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
        DependenciesScanner.mockClassDependencies = Set.of();

        compile(Map.of(
            "source/package-info", "" +
                "@DependsUpon(\"undefined\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsUpon;\n",
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "}\n"));

        expect(
            error("Invalid `DependsOn`: Unknown package [undefined].")
        );
    }

    @Test void shouldCompileClassWithAllowedDependency() {
        DependenciesScanner.mockClassDependencies = Set.of(new SimpleEntry<>("source.Source", "target.Target"));

        compile(Map.of(
            "source/package-info", "" +
                "@DependsUpon(\"target\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsUpon;\n",
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target target;\n" +
                "}\n",
            "target/Target", "" +
                "package target;\n" +
                "\n" +
                "public class Target {\n" +
                "}\n"));

        expect();
    }

    @Test void shouldWarnAboutClassWithUnusedDependency() {
        DependenciesScanner.mockClassDependencies = Set.of();

        compile(Map.of(
            "source/package-info", "" +
                "@DependsUpon(\"target\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsUpon;\n",
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "public class Source {\n" +
                "}\n",
            "target/Target", "" +
                "package target;\n" +
                "\n" +
                "public class Target {\n" +
                "}\n"));

        expect(
            warning("Unused dependency [source] -> [target]")
        );
    }

    @Test void shouldCompileEnumWithAllowedDependency() {
        DependenciesScanner.mockClassDependencies = Set.of(new SimpleEntry<>("source.Source", "target.Target"));

        compile(Map.of(
            "source/package-info", "" +
                "@DependsUpon(\"target\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsUpon;\n",
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "enum Source {\n" +
                "    FOO;\n" +
                "    private Target target;\n" +
                "}\n",
            "target/Target", "" +
                "package target;\n" +
                "\n" +
                "public class Target {\n" +
                "}\n"));

        expect();
    }

    @Test void shouldCompileAnnotationWithAllowedDependency() {
        DependenciesScanner.mockClassDependencies = Set.of(new SimpleEntry<>("source.Source", "target.Target"));

        compile(Map.of(
            "source/package-info", "" +
                "@DependsUpon(\"target\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsUpon;\n",
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public @interface Source {\n" +
                "    Class<Target> value();\n" +
                "}\n",
            "target/Target", "" +
                "package target;\n" +
                "\n" +
                "public class Target {\n" +
                "}\n"));

        expect();
    }

    @Test void shouldCompileInterfaceWithAllowedDependency() {
        DependenciesScanner.mockClassDependencies = Set.of(new SimpleEntry<>("source.Source", "target.Target"));

        compile(Map.of(
            "source/package-info", "" +
                "@DependsUpon(\"target\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsUpon;\n",
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public interface Source extends Target {\n" +
                "}\n",
            "target/Target", "" +
                "package target;\n" +
                "\n" +
                "public interface Target {\n" +
                "}\n"));

        expect();
    }

    @Test void shouldFailWithTwoDisallowedDependencies() {
        DependenciesScanner.mockClassDependencies = Set.of(
            new SimpleEntry<>("source.Source", "target1.Target1a"),
            new SimpleEntry<>("source.Source", "target1.Target1b"),
            new SimpleEntry<>("source.Source", "target2.Target2"),
            new SimpleEntry<>("source.Source", "target3.Target3")
        );

        compile(Map.of(
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
                "}\n"));

        expect(
            error("Forbidden dependency [source] -> [target1]"),
            error("Forbidden dependency [source] -> [target2]")
        );
    }
}

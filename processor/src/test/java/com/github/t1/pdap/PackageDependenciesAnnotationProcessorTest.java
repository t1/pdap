package com.github.t1.pdap;

import org.junit.jupiter.api.Test;

import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Set;

class PackageDependenciesAnnotationProcessorTest extends AbstractAnnotationProcessorTest {
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

    @Test void shouldSucceedAllowedDependency() {
        pdap.actualDependencies = Set.of(new SimpleEntry<>("source", "target"));
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

    @Test void shouldFailWithTwoDisallowedDependencies() {
        pdap.actualDependencies = Set.of(new SimpleEntry<>("source", "target1"), new SimpleEntry<>("source", "target2"));
        compile(Map.of(
            "source/package-info", "" +
                "@DependsUpon(\"\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsUpon;\n",
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "import target1.Target1a;\n" +
                "import target1.Target1b;\n" +
                "import target2.Target2;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target1a target1a;\n" +
                "    private Target1b target1b;\n" +
                "    private Target2 target2;\n" +
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
                "}\n"));

        expect(
            error("Forbidden dependency from [source] to [target1]"),
            error("Forbidden dependency from [source] to [target2]")
        );
    }
}

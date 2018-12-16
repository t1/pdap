package com.github.t1.pdap;

import org.junit.jupiter.api.Test;

import java.util.Map;

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
            error("Invalid dependency. Unknown package [undefined].")
        );
    }

    @Test void shouldSucceedAllowedDependency() {
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
                "import source.Source;\n" +
                "\n" +
                "public class Target {\n" +
                "    private Source source;\n" +
                "}\n"));

        expect();
    }

    @Test void shouldFailDisallowedDependency() {
        compile(Map.of(
            "source/package-info", "" +
                "@DependsUpon(\"xxx\")\n" +
                "package source;\n" +
                "\n" +
                "import com.github.t1.pdap.DependsUpon;\n",
            "target/Target", "" +
                "package target;\n" +
                "\n" +
                "import source.Source;\n" +
                "\n" +
                "public class Target {\n" +
                "    private Source source;\n" +
                "}\n",
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target target;\n" +
                "}\n"));

        expect(
            note("can depend upon [xxx]"),
            error("Invalid dependency. Unknown package [xxx].")
        );
    }
}

package com.github.t1.pdap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaCompiler.CompilationTask;
import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class PackageDependenciesAnnotationProcessorTest {
    private List<DiagnosticMatch> compile(String fileName, String source) { return compile(Map.of(fileName, source)); }

    private List<DiagnosticMatch> compile(Map<String, String> files) {
        return compile(files.entrySet().stream()
            .map(entry -> new StringJavaFileObject(Paths.get(entry.getKey() + ".java"), entry.getValue()))
            .collect(Collectors.toList()));
    }

    private List<DiagnosticMatch> compile(List<JavaFileObject> compilationUnits) {
        List<DiagnosticMatch> diagnostics = new ArrayList<>();

        DiagnosticListener<JavaFileObject> diagnosticListener = diagnostic -> {
            System.out.println(diagnostic.getKind() + " [" + diagnostic.getCode() + "] " + diagnostic.getMessage(Locale.getDefault()));
            diagnostics.add(new DiagnosticMatch(diagnostic));
        };
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        NoOutputFileManager fileManager = new NoOutputFileManager(compiler.getStandardFileManager(null, null, null));

        CompilationTask task = compiler.getTask(null, fileManager, diagnosticListener, List.of("-Xlint:all"), null, compilationUnits);
        task.setProcessors(List.of(new PackageDependenciesAnnotationProcessor()));
        task.call();

        return diagnostics;
    }

    private List<DiagnosticMatch> errors(List<DiagnosticMatch> diagnostics) {
        return diagnostics.stream().filter(diagnostic -> is(diagnostic, Kind.ERROR)).collect(Collectors.toList());
    }

    private List<DiagnosticMatch> warnings(List<DiagnosticMatch> diagnostics) {
        return diagnostics.stream().filter(diagnostic -> is(diagnostic, Kind.WARNING) || is(diagnostic, Kind.MANDATORY_WARNING)).collect(Collectors.toList());
    }

    private boolean is(DiagnosticMatch diagnostic, Kind warning) { return diagnostic.getKind() == warning; }

    private DiagnosticMatch warning(String message) {
        return new DiagnosticMatch(Kind.WARNING, null, -1, -1, -1, -1, -1, "compiler.warn.proc.messager", message);
    }

    private DiagnosticMatch warning(String source, long position, long startPosition, long endPosition, long lineNumber, long columnNumber, String code, String message) {
        return new DiagnosticMatch(Kind.WARNING, source, position, startPosition, endPosition, lineNumber, columnNumber, code, message);
    }

    private DiagnosticMatch error(String source, long position, long startPosition, long endPosition, long lineNumber, long columnNumber, String code, String message) {
        return new DiagnosticMatch(Kind.ERROR, source, position, startPosition, endPosition, lineNumber, columnNumber, code, message);
    }

    private DiagnosticMatch error(String message) {
        return new DiagnosticMatch(Kind.ERROR, null, -1, -1, -1, -1, -1, "compiler.err.proc.messager", message);
    }


    @Test void shouldSucceedWithoutAnnotations() {
        List<DiagnosticMatch> diagnostics = compile("SucceedAnnotationProcessing", "" +
            "public class SucceedAnnotationProcessing {\n" +
            "}");

        assertThat(diagnostics).isEmpty();
    }

    @Test void shouldFailToCompileWithUnknownSymbol() {
        List<DiagnosticMatch> diagnostics = compile(
            "FailAnnotationProcessing", "" +
                "@UnknownAnnotation\n" +
                "public class FailAnnotationProcessing {\n" +
                "}");

        assertThat(diagnostics).containsExactly(
            warning(null, -1, -1, -1, -1, -1, "compiler.warn.proc.annotations.without.processors", "No processor claimed any of these annotations: UnknownAnnotation"),
            error("/FailAnnotationProcessing.java", 1, 1, 18, 1, 2, "compiler.err.cant.resolve", "cannot find symbol\n  symbol: class UnknownAnnotation"));
    }

    @Test void shouldFailInvalidDependsUpon() {
        List<DiagnosticMatch> diagnostics = compile(Map.of(
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

        assertThat(errors(diagnostics)).containsExactly(
            error("Invalid dependency. Unknown package [undefined].")
        );
    }

    @Test void shouldSucceedAllowedDependency() {
        List<DiagnosticMatch> diagnostics = compile(Map.of(
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

        assertThat(errors(diagnostics)).isEmpty();
        assertThat(warnings(diagnostics)).containsExactly(
            warning("can depend upon [target]")
        );
    }

    @Test void shouldFailDisallowedDependency() {
        List<DiagnosticMatch> diagnostics = compile(Map.of(
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

        assertThat(warnings(diagnostics)).containsExactly(
            warning("can depend upon [xxx]")
        );
    }

    @Data
    @Builder
    @AllArgsConstructor
    private static class DiagnosticMatch {
        private Kind kind;
        private String source;
        private long position;
        private long startPosition;
        private long endPosition;
        private long lineNumber;
        private long columnNumber;
        private String code;
        private String message;

        DiagnosticMatch(Diagnostic<? extends JavaFileObject> diagnostic) {
            this.kind = diagnostic.getKind();
            this.source = (diagnostic.getSource() == null) ? null : diagnostic.getSource().getName();
            this.position = diagnostic.getPosition();
            this.startPosition = diagnostic.getStartPosition();
            this.endPosition = diagnostic.getEndPosition();
            this.lineNumber = diagnostic.getLineNumber();
            this.columnNumber = diagnostic.getColumnNumber();
            this.code = diagnostic.getCode();
            this.message = diagnostic.getMessage(Locale.getDefault());
        }
    }
}

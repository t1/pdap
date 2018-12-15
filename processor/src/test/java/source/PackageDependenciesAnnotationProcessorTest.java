package source;

import com.github.t1.pdap.PackageDependenciesAnnotationProcessor;
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
import java.util.stream.Collectors;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

class PackageDependenciesAnnotationProcessorTest {
    private List<DiagnosticMatch> compile(String fileName, String source) {
        List<DiagnosticMatch> diagnostics = new ArrayList<>();
        DiagnosticListener<JavaFileObject> diagnosticListener = diagnostic -> diagnostics.add(new DiagnosticMatch(diagnostic));
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        NoOutputFileManager fileManager = new NoOutputFileManager(compiler.getStandardFileManager(null, null, null));
        List<JavaFileObject> compilationUnits = new ArrayList<>();
        compilationUnits.add(new StringJavaFileObject(Paths.get(fileName + ".java"), source));
        CompilationTask task = compiler.getTask(null, fileManager, diagnosticListener, singletonList("-Xlint:all"), null, compilationUnits);
        task.setProcessors(singleton(new PackageDependenciesAnnotationProcessor()));
        task.call();
        return diagnostics;
    }

    private List<DiagnosticMatch> errors(List<DiagnosticMatch> diagnostics) {
        return diagnostics.stream().filter(diagnostic -> diagnostic.getKind() == Kind.ERROR).collect(Collectors.toList());
    }

    private DiagnosticMatch error(String source, long position, long startPosition, long endPosition, long lineNumber, long columnNumber, String code, String message) {
        return new DiagnosticMatch(Kind.ERROR, source, position, startPosition, endPosition, lineNumber, columnNumber, code, message);
    }

    private DiagnosticMatch warning(String source, long position, long startPosition, long endPosition, long lineNumber, long columnNumber, String code, String message) {
        return new DiagnosticMatch(Kind.WARNING, source, position, startPosition, endPosition, lineNumber, columnNumber, code, message);
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

    @Test void shouldSucceedAWithB() {
        List<DiagnosticMatch> diagnostics = compile("SucceedAnnotationProcessing", "" +
            "import com.github.t1.pdap.A;\n" +
            "import com.github.t1.pdap.B;\n" +
            "\n" +
            "@A @B\n" +
            "public class SucceedAnnotationProcessing {\n" +
            "}");

        assertThat(errors(diagnostics)).isEmpty();
    }

    @Test void shouldFailAWithoutB() {
        List<DiagnosticMatch> diagnostics = compile(
            "FailAnnotationProcessing", "" +
                "import com.github.t1.pdap.A;\n" +
                "\n" +
                "@A\n" +
                "public class FailAnnotationProcessing {\n" +
                "}");

        assertThat(errors(diagnostics)).containsExactly(error(null, -1, -1, -1, -1, -1, "compiler.err.proc.messager", "Annotation A must be accompanied by annotation B"));
    }

    @Test void shouldSucceedAllowedDependency() {
        List<DiagnosticMatch> diagnostics = compile(
            "source/Source", "" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target target;\n" +
                "}\n");

        assertThat(errors(diagnostics)).isEmpty();
    }

    @Test void shouldFailDisallowedDependency() {
        List<DiagnosticMatch> diagnostics = compile(
            "target.Target2", "" +
                "package target;\n" +
                "\n" +
                "import source.Source;\n" +
                "\n" +
                "public class Target2 {\n" +
                "    private Source source;\n" +
                "}\n");

        assertThat(errors(diagnostics)).containsExactly(
            error("/target.Target2.java", 47, 40, 91, 5, 8, "compiler.err.class.public.should.be.in.file", "class Target2 is public, should be declared in a file named Target2.java"),
            error("/target.Target2.java", 30, 24, 37, 3, 14, "compiler.err.cant.resolve.location", "cannot find symbol\n" +
                "  symbol:   class Source\n" +
                "  location: package source"),
            error("/target.Target2.java", 75, 75, 81, 6, 13, "compiler.err.cant.resolve.location", "cannot find symbol\n" +
                "  symbol:   class Source\n" +
                "  location: class target.Target2")
        );
        // "/target/Target2.java:3: error: cannot find symbol\n" +
        // "import source.Source;\n" +
        // "             ^\n" +
        // "  symbol:   class Source\n" +
        // "  location: package source\n" +
        // "/target/Target2.java:6: error: cannot find symbol\n" +
        // "    private Source source;\n" +
        // "            ^\n" +
        // "  symbol:   class Source\n" +
        // "  location: class Target2\n" +
        // "2 errors\n");
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

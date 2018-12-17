package com.github.t1.pdap;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

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
import java.util.Map;
import java.util.function.Predicate;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class AbstractAnnotationProcessorTest {
    @Data
    @Builder
    @AllArgsConstructor
    static class DiagnosticMatch {
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
            this.message = diagnostic.getMessage(null);
        }
    }

    private final List<DiagnosticMatch> diagnostics = new ArrayList<>();
    PackageDependenciesAnnotationProcessor pdap = new PackageDependenciesAnnotationProcessor();

    void compile(String fileName, String source) { compile(Map.of(fileName, source)); }

    void compile(Map<String, String> files) {
        compile(files.entrySet().stream()
            .map(entry -> new StringJavaFileObject(Paths.get(entry.getKey() + ".java"), entry.getValue()))
            .collect(toList()));
    }

    void compile(List<JavaFileObject> compilationUnits) {
        DiagnosticListener<JavaFileObject> diagnosticListener = diagnostic -> {
            System.out.println(diagnostic.getKind() + " [" + diagnostic.getCode() + "] " + diagnostic.getMessage(null));
            diagnostics.add(new DiagnosticMatch(diagnostic));
        };
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        NoOutputFileManager fileManager = new NoOutputFileManager(compiler.getStandardFileManager(null, null, null));

        CompilationTask task = compiler.getTask(null, fileManager, diagnosticListener, List.of("-Xlint:all"), null, compilationUnits);
        task.setProcessors(List.of(pdap));
        task.call();
    }


    /**
     * Check that all these diagnostics have been reported, and no other errors or warning.
     * Note that JavacMessager.printMessage maps OTHER to NOTE, so we can't check for all notes but not others :-(
     */
    void expect(DiagnosticMatch... expectedDiagnostics) {
        List<DiagnosticMatch> expectedList = new ArrayList<>(asList(expectedDiagnostics));
        assertThat(errors(diagnostics)).describedAs("errors").containsOnlyElementsOf(errors(expectedList));
        assertThat(warnings(diagnostics)).describedAs("warnings").containsOnlyElementsOf(warnings(expectedList));
        assertThat(notes(diagnostics)).describedAs("notes").containsAll(notes(expectedList));
        assertThat(diagnostics).allMatch(this::isNoteOrOther);
        assertThat(expectedList).isEmpty();
    }

    private boolean isError(DiagnosticMatch diagnostic) { return is(diagnostic, Kind.ERROR); }

    private boolean isWarning(DiagnosticMatch diagnostic) { return is(diagnostic, Kind.WARNING, Kind.MANDATORY_WARNING); }

    private boolean isNoteOrOther(DiagnosticMatch diagnostic) { return is(diagnostic, Kind.NOTE, Kind.OTHER); }

    private List<DiagnosticMatch> errors(List<DiagnosticMatch> diagnostics) { return split(diagnostics, this::isError); }

    private List<DiagnosticMatch> warnings(List<DiagnosticMatch> diagnostics) { return split(diagnostics, this::isWarning); }

    private List<DiagnosticMatch> notes(List<DiagnosticMatch> diagnostics) { return split(diagnostics, this::isNoteOrOther); }

    private List<DiagnosticMatch> split(List<DiagnosticMatch> diagnostics, Predicate<DiagnosticMatch> predicate) {
        List<DiagnosticMatch> matches = diagnostics.stream().filter(predicate).collect(toList());
        diagnostics.removeAll(matches);
        return matches;
    }

    private boolean is(DiagnosticMatch diagnostic, Kind... kind) { return asList(kind).contains(diagnostic.getKind()); }


    DiagnosticMatch error(String message) {
        return error(null, -1, -1, -1, -1, -1, "compiler.err.proc.messager", message);
    }

    DiagnosticMatch error(String source, long position, long startPosition, long endPosition, long lineNumber, long columnNumber, String code, String message) {
        return new DiagnosticMatch(Kind.ERROR, source, position, startPosition, endPosition, lineNumber, columnNumber, code, message);
    }


    DiagnosticMatch warning(String message) { return warning("compiler.warn.proc.messager", message); }

    DiagnosticMatch warning(String code, String message) {
        return warning(null, -1, -1, -1, -1, -1, code, message);
    }

    DiagnosticMatch warning(String source, long position, long startPosition, long endPosition, long lineNumber, long columnNumber, String code, String message) {
        return new DiagnosticMatch(Kind.WARNING, source, position, startPosition, endPosition, lineNumber, columnNumber, code, message);
    }


    DiagnosticMatch note(String message) {
        return new DiagnosticMatch(Kind.NOTE, null, -1, -1, -1, -1, -1, "compiler.note.proc.messager", message);
    }
}

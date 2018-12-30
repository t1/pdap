package com.github.t1.pdap;

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
import java.util.Objects;
import java.util.function.Predicate;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"SameParameterValue", "unused"})
class AbstractAnnotationProcessorTest {
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
            this(diagnostic.getKind(),
                (diagnostic.getSource() == null) ? null : diagnostic.getSource().getName(),
                diagnostic.getPosition(),
                diagnostic.getStartPosition(),
                diagnostic.getEndPosition(),
                diagnostic.getLineNumber(),
                diagnostic.getColumnNumber(),
                diagnostic.getCode(),
                diagnostic.getMessage(null));
        }

        //<editor-fold desc="Standard constructor, equals, hashcode, toString">
        DiagnosticMatch(Kind kind, String source, long position, long startPosition, long endPosition, long lineNumber, long columnNumber, String code, String message) {
            this.kind = kind;
            this.source = source;
            this.position = position;
            this.startPosition = startPosition;
            this.endPosition = endPosition;
            this.lineNumber = lineNumber;
            this.columnNumber = columnNumber;
            this.code = code;
            this.message = message;
        }

        public boolean equals(final Object o) {
            if (o == this)
                return true;
            if (!(o instanceof DiagnosticMatch))
                return false;
            final DiagnosticMatch other = (DiagnosticMatch) o;
            if (!other.canEqual(this))
                return false;
            final Object this$kind = this.kind;
            final Object other$kind = other.kind;
            if (!Objects.equals(this$kind, other$kind))
                return false;
            final Object this$source = this.source;
            final Object other$source = other.source;
            if (!Objects.equals(this$source, other$source))
                return false;
            if (this.position != other.position)
                return false;
            if (this.startPosition != other.startPosition)
                return false;
            if (this.endPosition != other.endPosition)
                return false;
            if (this.lineNumber != other.lineNumber)
                return false;
            if (this.columnNumber != other.columnNumber)
                return false;
            final Object this$code = this.code;
            final Object other$code = other.code;
            if (!Objects.equals(this$code, other$code))
                return false;
            final Object this$message = this.message;
            final Object other$message = other.message;
            return Objects.equals(this$message, other$message);
        }

        boolean canEqual(final Object other) {return other instanceof DiagnosticMatch;}

        public int hashCode() {
            final int PRIME = 59;
            int result = 1;
            final Object $kind = this.kind;
            result = result * PRIME + ($kind == null ? 43 : $kind.hashCode());
            final Object $source = this.source;
            result = result * PRIME + ($source == null ? 43 : $source.hashCode());
            final long $position = this.position;
            result = result * PRIME + (int) ($position >>> 32 ^ $position);
            final long $startPosition = this.startPosition;
            result = result * PRIME + (int) ($startPosition >>> 32 ^ $startPosition);
            final long $endPosition = this.endPosition;
            result = result * PRIME + (int) ($endPosition >>> 32 ^ $endPosition);
            final long $lineNumber = this.lineNumber;
            result = result * PRIME + (int) ($lineNumber >>> 32 ^ $lineNumber);
            final long $columnNumber = this.columnNumber;
            result = result * PRIME + (int) ($columnNumber >>> 32 ^ $columnNumber);
            final Object $code = this.code;
            result = result * PRIME + ($code == null ? 43 : $code.hashCode());
            final Object $message = this.message;
            result = result * PRIME + ($message == null ? 43 : $message.hashCode());
            return result;
        }

        public String toString() {return "AbstractAnnotationProcessorTest.DiagnosticMatch(kind=" + this.kind + ", source=" + this.source + ", position=" + this.position + ", startPosition=" + this.startPosition + ", endPosition=" + this.endPosition + ", lineNumber=" + this.lineNumber + ", columnNumber=" + this.columnNumber + ", code=" + this.code + ", message=" + this.message + ")";}
        //</editor-fold>
    }

    private final List<DiagnosticMatch> diagnostics = new ArrayList<>();
    private PackageDependenciesAnnotationProcessor pdap = new PackageDependenciesAnnotationProcessor();

    StringJavaFileObject file(String file1, String source1) {
        return new StringJavaFileObject(Paths.get(file1 + ".java"), source1);
    }

    void compile(JavaFileObject... compilationUnits) {
        DiagnosticListener<JavaFileObject> diagnosticListener = diagnostic -> {
            System.out.println(diagnostic.getKind() + " [" + diagnostic.getCode() + "] " + diagnostic.getMessage(null)
                + ((diagnostic.getSource() == null) ? ""
                : (" @ " + diagnostic.getSource().getName() + ":" + diagnostic.getLineNumber() + "," + diagnostic.getColumnNumber())));
            diagnostics.add(new DiagnosticMatch(diagnostic));
        };
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        NoOutputFileManager fileManager = new NoOutputFileManager(compiler.getStandardFileManager(diagnosticListener, null, null));

        CompilationTask task = compiler.getTask(null, fileManager, diagnosticListener, asList("-Xlint:all", "-source", "8", "-target", "8"),
            null, asList(compilationUnits));
        task.setProcessors(singletonList(pdap));
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

    private boolean is(DiagnosticMatch diagnostic, Kind... kind) { return asList(kind).contains(diagnostic.kind); }


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
        return new DiagnosticMatch(Kind.NOTE, null, -1, -1, -1, -1, -1,
            "compiler.note.proc.messager", message);
    }
}

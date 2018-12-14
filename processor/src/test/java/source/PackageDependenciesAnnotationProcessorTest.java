package source;

import com.github.t1.pdap.PackageDependenciesAnnotationProcessor;
import org.joor.CompileOptions;
import org.joor.Reflect;
import org.joor.ReflectException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class PackageDependenciesAnnotationProcessorTest {
    private void compile(String name, String content) {
        Reflect.compile(name, content, new CompileOptions().processors(new PackageDependenciesAnnotationProcessor())).create().get();
    }

    @Test void shouldSucceedWithout() {
        compile("SucceedAnnotationProcessing", "" +
            "public class SucceedAnnotationProcessing {\n" +
            "}");
    }

    @Test void shouldFailToCompileWithUnknownSymbol() {
        Throwable throwable = catchThrowable(() -> compile(
            "FailAnnotationProcessing", "" +
                "@UnknownAnnotation\n" +
                "public class FailAnnotationProcessing {\n" +
                "}"));

        assertThat(throwable).isExactlyInstanceOf(ReflectException.class).hasMessageContaining("" +
            "/FailAnnotationProcessing.java:1: error: cannot find symbol\n" +
            "@UnknownAnnotation\n" +
            " ^\n" +
            "  symbol: class UnknownAnnotation\n" +
            "1 error\n");
    }

    @Test void shouldSucceedAWithB() {
        compile("SucceedAnnotationProcessing", "" +
            "import com.github.t1.pdap.A;\n" +
            "import com.github.t1.pdap.B;\n" +
            "\n" +
            "@A @B\n" +
            "public class SucceedAnnotationProcessing {\n" +
            "}");
    }

    @Test void shouldFailAWithoutB() {
        Throwable throwable = catchThrowable(() -> compile(
            "FailAnnotationProcessing", "" +
                "import com.github.t1.pdap.A;\n" +
                "\n" +
                "@A\n" +
                "public class FailAnnotationProcessing {\n" +
                "}"));

        assertThat(throwable).isExactlyInstanceOf(ReflectException.class)
            .hasMessageContaining("Annotation A must be accompanied by annotation B")
            .hasMessageContaining("1 error");
    }

    @Test void shouldSucceedAllowedDependency() {
        compile(
            "source.Source", "" +
                "package source;\n" +
                "\n" +
                "import target.Target;\n" +
                "\n" +
                "public class Source {\n" +
                "    private Target target;\n" +
                "}\n");
    }

    @Test void shouldFailDisallowedDependency() {
        Throwable throwable = catchThrowable(() -> compile(
            "target.Target2", "" +
                "package target;\n" +
                "\n" +
                "import source.Source;\n" +
                "\n" +
                "public class Target2 {\n" +
                "    private Source source;\n" +
                "}\n"));

        assertThat(throwable).isExactlyInstanceOf(ReflectException.class).hasMessageContaining("" +
            "/target/Target2.java:3: error: cannot find symbol\n" +
            "import source.Source;\n" +
            "             ^\n" +
            "  symbol:   class Source\n" +
            "  location: package source\n" +
            "/target/Target2.java:6: error: cannot find symbol\n" +
            "    private Source source;\n" +
            "            ^\n" +
            "  symbol:   class Source\n" +
            "  location: class Target2\n" +
            "2 errors\n");
    }
}

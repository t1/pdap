import com.github.t1.pdap.PackageDependenciesAnnotationProcessor;
import org.joor.CompileOptions;
import org.joor.Reflect;
import org.joor.ReflectException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class PackageDependenciesAnnotationProcessorTest {
    private final PackageDependenciesAnnotationProcessor p = new PackageDependenciesAnnotationProcessor();

    @Test void shouldFailAnnotationProcessing() {
        Throwable throwable = catchThrowable(() -> compile(
            "FailAnnotationProcessing", "" +
                "import com.github.t1.pdap.A;\n" +
                "\n" +
                "@A\n" +
                "public class FailAnnotationProcessing {\n" +
                "}"));

        assertThat(throwable).isExactlyInstanceOf(ReflectException.class).hasMessage("" +
            "Compilation error: /FailAnnotationProcessing.java:2: error: Annotation A must be accompanied by annotation B\n" +
            "public class FailAnnotationProcessing {\n" +
            "       ^\n" +
            "1 error\n");
    }

    @Test void shouldSucceedAnnotationProcessing() {
        compile("SucceedAnnotationProcessing", ""
            + "import com.github.t1.pdap.A;\n" +
            "import com.github.t1.pdap.B;\n" +
            "\n" +
            "@A @B\n" +
            "public class SucceedAnnotationProcessing {\n" +
            "}");
    }

    private void compile(String name, String content) {
        Reflect.compile(name, content, new CompileOptions().processors(p)).create().get();
    }
}

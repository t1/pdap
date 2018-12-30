package com.github.t1.pdap;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;
import java.util.function.Supplier;

@SuppressWarnings({"WeakerAccess", "unused"})
public abstract class AbstractAnnotationProcessor extends AbstractProcessor {
    @Override public SourceVersion getSupportedSourceVersion() {
        return this.getClass().isAnnotationPresent(SupportedSourceVersion.class)
            ? super.getSupportedSourceVersion() : SourceVersion.latestSupported();
    }

    protected void debug(Supplier<String> message) {
        if (isDebugEnabled())
            other("[DEBUG] " + message.get());
    }

    protected boolean isDebugEnabled() { return Boolean.getBoolean(getClass().getCanonicalName() + "#DEBUG"); }

    protected void other(String message) { print(Kind.OTHER, message); }

    protected void other(String message, Element element) { print(Kind.OTHER, message, element); }

    protected void note(String message) { print(Kind.NOTE, message); }

    protected void note(String message, Element element) { print(Kind.NOTE, message, element); }

    protected void mandatoryWarning(String message) { print(Kind.MANDATORY_WARNING, message); }

    protected void mandatoryWarning(String message, Element element) { print(Kind.MANDATORY_WARNING, message, element); }

    protected void warning(String message) { print(Kind.WARNING, message); }

    protected void warning(String message, Element element) { print(Kind.WARNING, message, element); }

    protected void error(String message) { print(Kind.ERROR, message); }

    protected void error(String message, Element element) { print(Kind.ERROR, message, element); }

    protected void print(Kind kind, String message) {
        processingEnv.getMessager().printMessage(kind, message);
    }

    protected void print(Kind kind, String message, Element element) {
        processingEnv.getMessager().printMessage(kind, message, element);
    }


    protected Elements getElementUtils() {
        return processingEnv.getElementUtils();
    }
}

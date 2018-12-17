package com.github.t1.pdap;

import javax.annotation.processing.AbstractProcessor;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic.Kind;

public abstract class AbstractAnnotationProcessor extends AbstractProcessor {

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

    private void print(Kind kind, String message) {
        processingEnv.getMessager().printMessage(kind, message);
    }

    private void print(Kind kind, String message, Element element) {
        processingEnv.getMessager().printMessage(kind, message);
    }


    protected Elements getElementUtils() {
        return processingEnv.getElementUtils();
    }
}

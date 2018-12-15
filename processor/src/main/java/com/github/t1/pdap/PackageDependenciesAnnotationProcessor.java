package com.github.t1.pdap;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static javax.lang.model.SourceVersion.RELEASE_11;

@SupportedSourceVersion(RELEASE_11)
@SupportedAnnotationTypes("com.github.t1.pdap.*")
public class PackageDependenciesAnnotationProcessor extends AbstractAnnotationProcessor {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        note("process annotations " + annotations);
        for (Element element : roundEnv.getRootElements()) {
            if (element.getAnnotation(A.class) != null && element.getAnnotation(B.class) == null) {
                error("Annotation A must be accompanied by annotation B", element);
            }

            other("process " + element.getKind() + " : " + element, element);
            if (element.getKind() == ElementKind.PACKAGE)
                continue;
            DependsUpon dependsUpon = findDependsUpon(element);
            if (dependsUpon != null) {
                List<String> packages = asList(dependsUpon.value());
                warning("depends upon " + packages, element);
            }
        }
        note("process end");
        Iterator<? extends TypeElement> iterator = annotations.iterator();
        return iterator.hasNext() && iterator.next().getQualifiedName().toString().equals(DependsUpon.class.getName());
    }

    // we do more casts than strictly necessary to document what's happening
    @SuppressWarnings("RedundantCast")
    private DependsUpon findDependsUpon(Element element) {
        switch (element.getKind()) {
            case PACKAGE:
                return element.getAnnotation(DependsUpon.class);
            case ENUM:
                break;
            case CLASS:
                return findDependsUpon((PackageElement) element.getEnclosingElement());
            case ANNOTATION_TYPE:
                break;
            case INTERFACE:
                break;
            case ENUM_CONSTANT:
                break;
            case FIELD:
                break;
            case PARAMETER:
                break;
            case LOCAL_VARIABLE:
                break;
            case EXCEPTION_PARAMETER:
                break;
            case METHOD:
                break;
            case CONSTRUCTOR:
                break;
            case STATIC_INIT:
                break;
            case INSTANCE_INIT:
                break;
            case TYPE_PARAMETER:
                break;
            case OTHER:
                break;
            case RESOURCE_VARIABLE:
                break;
            case MODULE:
                break;
        }
        error("don't know how to find DependsUpon for " + element.getKind() + " " + element);
        return null;
    }
}

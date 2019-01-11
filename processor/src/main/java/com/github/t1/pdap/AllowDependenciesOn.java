package com.github.t1.pdap;

import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PACKAGE;

@Target(PACKAGE)
public @interface AllowDependenciesOn {
    String[] value() default "";
}

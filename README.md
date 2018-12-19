# PDAP = Package Dependencies Annotation Processor

The dependencies between packages are important to keep an eye on, so your architecture stays a Clean Architecture.
E.g., a `boundary` package with your REST bindings is allowed to access the `controller` package with you business logic,
but not the other way around, or you will find it difficult to refactor, or even think clearly about your layers.

Yet I've hardly seen any larger code base, where these rules are *not* violated!
It's just way to easy to accidentally import a class from the wrong package.
There are tools to check your dependencies, but when exactly do you use them?
And do you really check the dependencies of a project with more than, e.g., 30 packages?

This little annotation processor comes to your rescue:
Just add a dependency to `com.github.t1:package.dependencies.annotation.processor:1.0.0-SNAPSHOT`
and annotate your packages (i.e. the `package-info.java` files introduced in Java 1.6) with `@DependsOn`, e.g.:

```java
@DependsUpon("controller")
package boundary;

import com.github.t1.pdap.DependsUpon;
```

... for you `boundary` package, and:

```java
@DependsUpon()
package controller;

import com.github.t1.pdap.DependsUpon;
```

... for your `controller` package.

The Java compiler detects the annotation processor in the dependency and executes it along with the compile process.
If you have a dependency that is not allowed, it will report it as a compilation error;
and if you have allowed a dependency that is not used, it will report it as a warning.

Note that packages without a `@DependsOn` annotation won't be checked at all,
which allows for a step-by-step introduction of dependency checking (you *will* find violations ;)


# Status

Alpha: There are still some features missing that are necessary to use in production:

* Get not only the imports but also the qualified names used within the code
* Super-Package-DependsOn: Define `DependsOn` on super packages, so all nested packages share these dependencies.
  This is essential, so you can add dependencies that are generally allowed, e.g. on libraries.

# Ideas

Some things that would be really cool to add:

* Report dependency cycles.
* Optionally report packages without `DependsOn`.
* Wildcards: `DependsOn("**.controller")` allows dependencies on all packages ending with `.controller`.
* `parent`-Variable: `DependsOn("${parent}.controller")` allows dependencies on a sibling `controller` package.

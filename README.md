# PDAP = Package Dependencies Annotation Processor

The dependencies between packages are important to keep an eye on, so your architecture stays a Clean Architecture.
E.g., a `boundary` package with your REST bindings is allowed to access the `controller` package with you business logic,
but not the other way around, or you will find it difficult to refactor, or even think clearly about your layers.

Yet I've hardly seen any larger code base, where these rules are *not* violated!
It's just way to easy to accidentally import a class from the wrong package.
There are tools to check your dependencies, but when exactly do you use them?
And do you really check the dependencies of a project with more than, e.g., 30 packages?

This little annotation processor comes to your rescue:
Just add a dependency to `com.github.t1:package.dependencies.annotation.processor`
and annotate your packages (i.e. in the `package-info.java` files introduced in Java 1.6) with `@DependsOn`, e.g.:

```java
@DependsOn("controller")
package boundary;

import com.github.t1.pdap.DependsOn;
```

... for you `boundary` package, and:

```java
@DependsOn()
package controller;

import com.github.t1.pdap.DependsOn;
```

... for your `controller` package.

The Java compiler detects the annotation processor in the dependency and executes it along with the compile process.
If you have a dependency that is not allowed, it will report it as a compilation error;
and if you have allowed a dependency that is not used, it will report it as a warning.

You can add `@DependsOn` annotations on super packages as well;
they will be merged with sub package annotations.
This allows you to declare generally allowed dependencies only once.

Note that packages without a `@DependsOn` annotation won't be checked at all,
which allows for a step-by-step introduction of dependency checking (and you *will* find violations ;)
A missing annotation on a leaf package will be reported as a warning.


# Eclipse

I haven't been using Eclipse for several years now, but it probably won't work with the Eclipse compiler,
as we access the Abstract Syntax Tree from the Java compiler, and Eclipse is not compatible with that.


# Status: Alpha

##### Qualified Names Not Recognized

Some qualified names used within the code *may* not be recognized.


##### Indirect Dependencies Are Not Detected

If you have three classes like this:

```java
package source;

import target1.Target1;

public class Source {
    private void foo() { Object target2 = new Target1().target2(); }
}
```

```java
package target1;

import target2.Target2;

public class Target1 {
    public Target2 target2() { return null; }
}
```

```java
package target2;

public class Target2 {
}
```

Then `Source` actually depends on `target1` as well as on `target2`.
But this is not directly in the Java abstract syntax tree (AST), this is only resolved when linking.
We still have to find out, how we can resolve this issue.


# Ideas

Some things that would be really cool to add:

* Report dependency cycles.
* Optionally report packages without `DependsOn`.
* Wildcards: `DependsOn("**.controller")` allows dependencies on all packages ending with `.controller`,
  and `@DependsOn("javax.ws.rs+")` allows dependencies on `javax.ws.rs` and all subpackages.
* `parent`-Variable: `DependsOn("${parent}.controller")` allows dependencies on a sibling `controller` package.

# Warnings and more

The `maven-compiler-plugin` normally doesn't show warnings. To see them, configure the plugin like this:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.8.0</version>
    <configuration>
        <showWarnings>true</showWarnings>
    </configuration>
</plugin>
``` 

This also enables `INFO` and `OTHER` messages, but to see debug output, set a system property, e.g.:

```
mvn clean install -Dcom.github.t1.pdap.PackageDependenciesAnnotationProcessor#DEBUG
```

package com.github.t1.pdap;

import javax.tools.SimpleJavaFileObject;
import java.net.URI;
import java.nio.file.Path;

class StringJavaFileObject extends SimpleJavaFileObject {
    private final String content;

    StringJavaFileObject(Path path, String content) {
        super(URI.create("string:///" + path), Kind.SOURCE);
        this.content = content;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
        return content;
    }
}

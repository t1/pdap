package com.github.t1.pdap;

import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import java.io.OutputStream;
import java.net.URI;

class NoOutputFileManager extends ForwardingJavaFileManager<StandardJavaFileManager> {
    NoOutputFileManager(StandardJavaFileManager fileManager) { super(fileManager); }

    @Override public JavaFileObject getJavaFileForOutput(Location location, String className, Kind kind, FileObject sibling) {
        return new NoOutputJavaFileObject(URI.create("string:///" + className.replace('.', '/') + kind.extension), kind);
    }

    private static final class NoOutputJavaFileObject extends SimpleJavaFileObject {
        NoOutputJavaFileObject(URI uri, Kind kind) { super(uri, kind); }

        @Override
        public OutputStream openOutputStream() {
            return new OutputStream() {
                @Override public void write(int b) {}
            };
        }
    }
}

package ru.tinkoff.kora.bpmn.camunda8.worker.util;

import java.io.InputStream;
import java.util.Objects;

record FileResource(String name, String path) implements Resource {

    @Override
    public InputStream asInputStream() {
        return FileResource.class.getResourceAsStream("/" + path + "/" + name);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof Resource that)) return false;
        return Objects.equals(name, that.name()) && Objects.equals(path, that.path());
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, path);
    }
}

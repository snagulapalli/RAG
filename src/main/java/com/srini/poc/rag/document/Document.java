package com.srini.poc.rag.document;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class Document {
    private final String content;
    private final Map<String, String> metadata;

    public Document(String content, Map<String, String> metadata) {
        this.content = Objects.requireNonNull(content, "content");
        this.metadata = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
    }

    public String getContent() {
        return content;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public String getSource() {
        return metadata.getOrDefault("source", "");
    }

    public Document withContent(String newContent) {
        return new Document(newContent, metadata);
    }

    public Document withMetadata(Map<String, String> extra) {
        Map<String, String> merged = new LinkedHashMap<>(metadata);
        merged.putAll(extra);
        return new Document(content, merged);
    }

    @Override
    public String toString() {
        return "Document{source=" + getSource() + ", chars=" + content.length() + "}";
    }
}

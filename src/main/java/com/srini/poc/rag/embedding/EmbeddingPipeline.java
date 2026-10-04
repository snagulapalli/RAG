package com.srini.poc.rag.embedding;

import com.srini.poc.rag.config.RagProperties;
import com.srini.poc.rag.document.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chunks documents and prepares text for embedding — mirrors python/src/embedding.py.
 */
@Service
public class EmbeddingPipeline {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingPipeline.class);
    private static final Pattern HEADER = Pattern.compile("^(#{1,3})\\s+(.*)$");

    private final int chunkSize;
    private final int chunkOverlap;

    public EmbeddingPipeline(RagProperties properties) {
        this.chunkSize = properties.chunkSize();
        this.chunkOverlap = properties.chunkOverlap();
    }

    public List<Document> chunkDocuments(List<Document> documents) {
        List<Document> chunks = new ArrayList<>();
        for (Document doc : documents) {
            if (doc.getSource().toLowerCase(Locale.ROOT).endsWith(".md")) {
                List<Document> sections = splitMarkdownByHeaders(doc);
                for (Document section : sections) {
                    String topic = Path.of(doc.getSource()).getFileName().toString()
                            .replaceAll("(?i)\\.md$", "")
                            .replace('-', ' ');
                    Document prefixed = section.withContent(topic + "\n" + section.getContent());
                    chunks.addAll(splitBySize(prefixed));
                }
            } else {
                chunks.addAll(splitBySize(doc));
            }
        }
        log.info("[INFO] Split {} documents into {} chunks.", documents.size(), chunks.size());
        return chunks;
    }

    private List<Document> splitMarkdownByHeaders(Document doc) {
        String[] lines = doc.getContent().split("\\R", -1);
        List<Document> sections = new ArrayList<>();
        String[] headers = new String[3]; // h1, h2, h3
        StringBuilder body = new StringBuilder();

        for (String line : lines) {
            Matcher m = HEADER.matcher(line);
            if (m.matches()) {
                flushSection(doc, sections, headers, body);
                int level = m.group(1).length();
                String title = m.group(2).trim();
                if (level == 1) {
                    headers[0] = title;
                    headers[1] = null;
                    headers[2] = null;
                } else if (level == 2) {
                    headers[1] = title;
                    headers[2] = null;
                } else {
                    headers[2] = title;
                }
            } else {
                body.append(line).append('\n');
            }
        }
        flushSection(doc, sections, headers, body);

        if (sections.isEmpty()) {
            sections.add(doc);
        }
        return sections;
    }

    private void flushSection(Document doc, List<Document> sections, String[] headers, StringBuilder body) {
        if (body.isEmpty()) {
            return;
        }
        Map<String, String> meta = new LinkedHashMap<>();
        if (headers[0] != null) {
            meta.put("Header 1", headers[0]);
        }
        if (headers[1] != null) {
            meta.put("Header 2", headers[1]);
        }
        if (headers[2] != null) {
            meta.put("Header 3", headers[2]);
        }
        sections.add(doc.withMetadata(meta).withContent(body.toString().trim()));
        body.setLength(0);
    }

    private List<Document> splitBySize(Document doc) {
        String text = doc.getContent();
        if (text.length() <= chunkSize) {
            return List.of(doc);
        }

        List<Document> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            if (end < text.length()) {
                int breakAt = findBreak(text, start, end);
                if (breakAt > start) {
                    end = breakAt;
                }
            }
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                parts.add(doc.withContent(chunk));
            }
            if (end >= text.length()) {
                break;
            }
            start = Math.max(end - chunkOverlap, start + 1);
        }
        return parts;
    }

    private int findBreak(String text, int start, int end) {
        String window = text.substring(start, end);
        for (String sep : List.of("\n\n", "\n", " ")) {
            int idx = window.lastIndexOf(sep);
            if (idx > chunkSize / 4) {
                return start + idx + sep.length();
            }
        }
        return end;
    }
}

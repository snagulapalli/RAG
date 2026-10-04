package com.srini.poc.rag.search;

import com.srini.poc.rag.config.RagProperties;
import com.srini.poc.rag.llm.GroqChatClient;
import com.srini.poc.rag.vectorstore.FaissVectorStore;
import com.srini.poc.rag.vectorstore.FaissVectorStore.SearchHit;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Retrieve + summarize — mirrors python/src/search.py RAGSearch.
 */
@Service
public class RagSearchService {

    private static final Logger log = LoggerFactory.getLogger(RagSearchService.class);

    private final FaissVectorStore vectorStore;
    private final GroqChatClient groqChatClient;
    private final double maxDistance;

    public RagSearchService(FaissVectorStore vectorStore, GroqChatClient groqChatClient, RagProperties properties) {
        this.vectorStore = vectorStore;
        this.groqChatClient = groqChatClient;
        this.maxDistance = properties.maxDistance();
    }

    @PostConstruct
    void init() {
        try {
            vectorStore.ensureReady();
        } catch (IllegalArgumentException e) {
            log.warn("[WARN] Vector store not ready: {}. Add files under data/ and call POST /api/reindex.", e.getMessage());
        }
    }

    public String searchAndSummarize(String query, int topK) {
        if (vectorStore.isEmpty()) {
            vectorStore.ensureReady();
        }

        List<SearchHit> results = vectorStore.query(query, topK).stream()
                .filter(r -> r.distance() < maxDistance)
                .toList();

        List<String> parts = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (SearchHit hit : results) {
            Map<String, String> m = hit.metadata();
            String src = m.getOrDefault("source", "");
            String name = Path.of(src.isBlank() ? "unknown" : src).getFileName().toString();
            String section = List.of("Header 1", "Header 2", "Header 3").stream()
                    .filter(m::containsKey)
                    .map(m::get)
                    .collect(Collectors.joining(" > "));
            log.info("[HIT] {}  {}  [{}]", String.format(Locale.ROOT, "%.3f", hit.distance()), name, section);

            if (src.toLowerCase(Locale.ROOT).endsWith(".md")) {
                if (!seen.add(src)) {
                    continue;
                }
                try {
                    String full = Files.readString(Path.of(src), StandardCharsets.UTF_8);
                    parts.add("[Source: " + name + " | full document]\n" + full);
                } catch (IOException e) {
                    log.warn("Could not read full markdown {}: {}", src, e.getMessage());
                    parts.add("[Source: " + name + " | page " + m.getOrDefault("page_label", "?") + "]\n"
                            + m.getOrDefault("text", ""));
                }
            } else {
                parts.add("[Source: " + name + " | page " + m.getOrDefault("page_label", "?") + "]\n"
                        + m.getOrDefault("text", ""));
            }
        }

        String context = String.join("\n\n", parts);
        if (context.isBlank()) {
            return "No relevant documents found.";
        }

        log.info("[CTX] {} passages, {} chars", parts.size(), context.length());

        String prompt = """
                You are answering questions about architecture decision records (ADRs). \
                Use ONLY the context below; do not add information, best practices, or recommendations \
                that are not stated in it. If the context does not answer the question, say so.

                The context may use different wording from the question; look for statements that answer the question in substance, not just in matching terms.

                Format your answer in two parts:
                1. Answer: a direct answer to the question.
                2. Related details: any rules, constraints, or examples from the context that relate to the answer, \
                quoted or closely paraphrased. If there are none, write 'None'.
                End with the source file name.

                Question: %s

                Context:
                %s

                Answer:""".formatted(query, context);

        return groqChatClient.chat(prompt);
    }
}

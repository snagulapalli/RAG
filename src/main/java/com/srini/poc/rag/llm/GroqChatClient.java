package com.srini.poc.rag.llm;

import com.srini.poc.rag.config.RagProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Groq Chat Completions client (OpenAI-compatible) — mirrors langchain_groq.ChatGroq.
 */
@Service
public class GroqChatClient {

    private static final Logger log = LoggerFactory.getLogger(GroqChatClient.class);

    private final ObjectMapper objectMapper;

    private final RagProperties properties;
    private RestClient restClient;

    public GroqChatClient(RagProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        if (properties.groqApiKey() != null && !properties.groqApiKey().isBlank()) {
            this.restClient = buildClient(properties);
            log.info("[INFO] Groq LLM initialized: {}", properties.llmModel());
        } else {
            log.warn("[WARN] GROQ_API_KEY is not set. Search/summarize will fail until it is configured.");
        }
    }

    public String chat(String prompt) {
        if (properties.groqApiKey() == null || properties.groqApiKey().isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY is not set. Export it or set rag.groq-api-key.");
        }
        if (restClient == null) {
            restClient = buildClient(properties);
        }

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.llmModel());
        ArrayNode messages = body.putArray("messages");
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", prompt);

        String response = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Groq response: " + response, e);
        }
    }

    private static RestClient buildClient(RagProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.groqBaseUrl())
                .defaultHeader("Authorization", "Bearer " + properties.groqApiKey())
                .build();
    }
}

package com.srini.poc.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        String dataDir,
        String persistDir,
        String embeddingModel,
        int chunkSize,
        int chunkOverlap,
        double maxDistance,
        String groqApiKey,
        String groqBaseUrl,
        String llmModel,
        boolean buildIndexOnStartup,
        boolean runQueryOnStartup,
        String defaultQuery,
        int defaultTopK
) {
}

package com.srini.poc.rag;

import com.srini.poc.rag.config.RagProperties;
import com.srini.poc.rag.search.RagSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Optional startup query — mirrors python/app.py CLI behavior.
 * Runs after {@link IndexBootstrapRunner} (@Order 2).
 */
@Component
@Order(2)
public class StartupQueryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupQueryRunner.class);

    private final RagSearchService ragSearchService;
    private final RagProperties properties;

    public StartupQueryRunner(RagSearchService ragSearchService, RagProperties properties) {
        this.ragSearchService = ragSearchService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.runQueryOnStartup()) {
            return;
        }

        String query = properties.defaultQuery();
        if (!args.getNonOptionArgs().isEmpty()) {
            query = String.join(" ", args.getNonOptionArgs());
        }

        try {
            String summary = ragSearchService.searchAndSummarize(query, properties.defaultTopK());
            System.out.println("Summary: " + summary);
        } catch (Exception e) {
            log.error("Startup query failed: {}", e.getMessage());
        }
    }
}

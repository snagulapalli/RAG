package com.srini.poc.rag.web;

import com.srini.poc.rag.config.RagProperties;
import com.srini.poc.rag.document.Document;
import com.srini.poc.rag.document.DocumentLoader;
import com.srini.poc.rag.search.RagSearchService;
import com.srini.poc.rag.vectorstore.FaissVectorStore;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RagController {

    private final RagSearchService ragSearchService;
    private final DocumentLoader documentLoader;
    private final FaissVectorStore vectorStore;
    private final RagProperties properties;

    public RagController(
            RagSearchService ragSearchService,
            DocumentLoader documentLoader,
            FaissVectorStore vectorStore,
            RagProperties properties
    ) {
        this.ragSearchService = ragSearchService;
        this.documentLoader = documentLoader;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    @PostMapping("/search")
    public SearchResponse search(@Valid @RequestBody SearchRequest request) {
        int topK = request.topK() == null ? properties.defaultTopK() : request.topK();
        String summary = ragSearchService.searchAndSummarize(request.query(), topK);
        return new SearchResponse(request.query(), summary);
    }

    @PostMapping("/reindex")
    public ResponseEntity<Map<String, Object>> reindex() {
        List<Document> docs = documentLoader.loadAllDocuments(properties.dataDir());
        vectorStore.buildFromDocuments(docs);
        return ResponseEntity.ok(Map.of(
                "documents", docs.size(),
                "persistDir", properties.persistDir(),
                "status", "ok"
        ));
    }
}

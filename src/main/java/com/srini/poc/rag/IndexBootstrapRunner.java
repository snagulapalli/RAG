package com.srini.poc.rag;

import com.srini.poc.rag.config.RagProperties;
import com.srini.poc.rag.document.Document;
import com.srini.poc.rag.document.DocumentLoader;
import com.srini.poc.rag.vectorstore.FaissVectorStore;
import com.srini.poc.rag.vectorstore.FaissVectorStore.SearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mirrors the (formerly commented) index-build block in python/app.py.
 *
 * Disable after indexes exist by either:
 * <ul>
 *   <li>setting {@code rag.build-index-on-startup: false} in application.yml, or</li>
 *   <li>commenting out the body inside {@link #run} between BEGIN/END markers</li>
 * </ul>
 */
@Component
@Order(1)
public class IndexBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IndexBootstrapRunner.class);

    private final DocumentLoader documentLoader;
    private final FaissVectorStore vectorStore;
    private final RagProperties properties;

    public IndexBootstrapRunner(
            DocumentLoader documentLoader,
            FaissVectorStore vectorStore,
            RagProperties properties
    ) {
        this.documentLoader = documentLoader;
        this.vectorStore = vectorStore;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.buildIndexOnStartup()) {
            log.info("[INDEX] Skipping index build (rag.build-index-on-startup=false)");
            return;
        }

        // =================================================================
        // BEGIN app.py index block — comment this section out once indexes exist
        // (or set rag.build-index-on-startup: false in application.yml)
        //
        // docs = load_all_documents("data")
        // store = FaissVectorStore("faiss_store")
        // store.build_from_documents(docs)
        // store.load()
        // print(store.query("What is attention mechanism?", top_k=5))
        // =================================================================
        List<Document> docs = documentLoader.loadAllDocuments(properties.dataDir());
        vectorStore.buildFromDocuments(docs);
        vectorStore.load();
        List<SearchHit> hits = vectorStore.query("What is attention mechanism?", 5);
        System.out.println("Index query results: " + hits);
        // =================================================================
        // END app.py index block
        // =================================================================
    }
}

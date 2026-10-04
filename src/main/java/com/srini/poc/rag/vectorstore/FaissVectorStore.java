package com.srini.poc.rag.vectorstore;

import com.srini.poc.rag.config.RagProperties;
import com.srini.poc.rag.document.Document;
import com.srini.poc.rag.document.DocumentLoader;
import com.srini.poc.rag.embedding.EmbeddingPipeline;
import com.srini.poc.rag.embedding.EmbeddingService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Flat L2 vector index with disk persistence — mirrors python/src/vectorstore.py (FAISS IndexFlatL2).
 */
@Service
public class FaissVectorStore {

    private static final Logger log = LoggerFactory.getLogger(FaissVectorStore.class);
    private static final Set<String> KEEP = Set.of("source", "page", "page_label", "Header 1", "Header 2", "Header 3");

    private final Path persistDir;
    private final DocumentLoader documentLoader;
    private final EmbeddingPipeline embeddingPipeline;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;

    private float[][] vectors = new float[0][];
    private List<Map<String, String>> metadata = new ArrayList<>();

    public FaissVectorStore(
            RagProperties properties,
            DocumentLoader documentLoader,
            EmbeddingPipeline embeddingPipeline,
            EmbeddingService embeddingService,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.persistDir = Path.of(properties.persistDir()).toAbsolutePath().normalize();
        this.documentLoader = documentLoader;
        this.embeddingPipeline = embeddingPipeline;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
    }

    public synchronized void ensureReady() {
        Path indexPath = persistDir.resolve("vectors.bin");
        Path metaPath = persistDir.resolve("metadata.json");
        List<Document> docs = documentLoader.loadAllDocuments(properties.dataDir());
        if (Files.exists(indexPath) && Files.exists(metaPath)) {
            load();
            if (isIndexStale(docs)) {
                log.warn("[WARN] Index is stale relative to data/. Rebuilding...");
                buildFromDocuments(docs);
            }
        } else {
            buildFromDocuments(docs);
        }
    }

    private boolean isIndexStale(List<Document> docs) {
        Set<String> indexed = metadata.stream()
                .map(m -> m.getOrDefault("source", ""))
                .filter(s -> !s.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        Set<String> onDisk = docs.stream()
                .map(Document::getSource)
                .filter(s -> !s.isBlank())
                .collect(java.util.stream.Collectors.toSet());
        return !indexed.equals(onDisk);
    }

    public synchronized void buildFromDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            throw new IllegalArgumentException("No documents found to index. Add files to the data directory.");
        }
        log.info("[INFO] Building vector store from {} raw documents...", documents.size());
        List<Document> chunks = embeddingPipeline.chunkDocuments(documents);
        List<String> texts = chunks.stream().map(Document::getContent).toList();
        float[][] embeddings = embeddingService.embedAll(texts);

        List<Map<String, String>> metas = new ArrayList<>();
        for (Document chunk : chunks) {
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("text", chunk.getContent());
            chunk.getMetadata().forEach((k, v) -> {
                if (KEEP.contains(k) && v != null) {
                    meta.put(k, v);
                }
            });
            metas.add(meta);
        }

        this.vectors = embeddings;
        this.metadata = metas;
        save();
        log.info("[INFO] Vector store built and saved to {}", persistDir);
    }

    public synchronized void save() {
        try {
            Files.createDirectories(persistDir);
            writeVectors(persistDir.resolve("vectors.bin"), vectors);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(persistDir.resolve("metadata.json").toFile(), metadata);
            log.info("[INFO] Saved vector index and metadata to {}", persistDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save vector store", e);
        }
    }

    public synchronized void load() {
        try {
            this.vectors = readVectors(persistDir.resolve("vectors.bin"));
            this.metadata = objectMapper.readValue(
                    persistDir.resolve("metadata.json").toFile(),
                    new TypeReference<>() {
                    }
            );
            log.info("[INFO] Loaded vector index ({} vectors) from {}", vectors.length, persistDir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load vector store", e);
        }
    }

    public synchronized List<SearchHit> query(String queryText, int topK) {
        log.info("[INFO] Querying vector store for: '{}'", queryText);
        float[] query = embeddingService.embed(queryText);
        return search(query, topK);
    }

    public synchronized List<SearchHit> search(float[] queryEmbedding, int topK) {
        if (vectors.length == 0) {
            return List.of();
        }
        List<SearchHit> scored = new ArrayList<>(vectors.length);
        for (int i = 0; i < vectors.length; i++) {
            scored.add(new SearchHit(i, l2(queryEmbedding, vectors[i]), metadata.get(i)));
        }
        scored.sort(Comparator.comparingDouble(SearchHit::distance));
        return scored.subList(0, Math.min(topK, scored.size()));
    }

    public boolean isEmpty() {
        return vectors.length == 0;
    }

    private static float l2(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            sum += d * d;
        }
        return (float) sum;
    }

    private static void writeVectors(Path path, float[][] vectors) throws IOException {
        int rows = vectors.length;
        int cols = rows == 0 ? 0 : vectors[0].length;
        ByteBuffer buf = ByteBuffer.allocate(8 + rows * cols * 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(rows);
        buf.putInt(cols);
        for (float[] row : vectors) {
            for (float v : row) {
                buf.putFloat(v);
            }
        }
        Files.write(path, buf.array());
    }

    private static float[][] readVectors(Path path) throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(Files.readAllBytes(path)).order(ByteOrder.LITTLE_ENDIAN);
        int rows = buf.getInt();
        int cols = buf.getInt();
        float[][] matrix = new float[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                matrix[i][j] = buf.getFloat();
            }
        }
        return matrix;
    }

    public record SearchHit(int index, float distance, Map<String, String> metadata) {
    }
}

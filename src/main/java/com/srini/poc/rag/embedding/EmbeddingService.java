package com.srini.poc.rag.embedding;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.onnx.allminilml6v2q.AllMiniLmL6V2QuantizedEmbeddingModel;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Local all-MiniLM-L6-v2 embeddings — same model family as Python sentence-transformers.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private AllMiniLmL6V2QuantizedEmbeddingModel model;

    @PostConstruct
    void init() {
        model = new AllMiniLmL6V2QuantizedEmbeddingModel();
        log.info("[INFO] Loaded embedding model: all-MiniLM-L6-v2 (quantized ONNX)");
    }

    public float[] embed(String text) {
        Embedding embedding = model.embed(text).content();
        return embedding.vector();
    }

    public float[][] embedAll(List<String> texts) {
        log.info("[INFO] Generating embeddings for {} chunks...", texts.size());
        List<Embedding> embeddings = model.embedAll(
                texts.stream().map(dev.langchain4j.data.segment.TextSegment::from).toList()
        ).content();

        float[][] matrix = new float[embeddings.size()][];
        for (int i = 0; i < embeddings.size(); i++) {
            matrix[i] = embeddings.get(i).vector();
        }
        if (matrix.length > 0) {
            log.info("[INFO] Embeddings shape: [{}, {}]", matrix.length, matrix[0].length);
        }
        return matrix;
    }

    public List<float[]> embedAllAsList(List<String> texts) {
        float[][] matrix = embedAll(texts);
        List<float[]> list = new ArrayList<>(matrix.length);
        for (float[] row : matrix) {
            list.add(row);
        }
        return list;
    }
}

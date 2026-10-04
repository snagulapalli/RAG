# RAG: privacy-preserving Q&A over Architecture Decision Records

A proof-of-concept retrieval-augmented generation (RAG) service. It answers natural-language questions about a folder of Architecture Decision Records (ADRs) and other documents, and every answer names the source it came from.

This repo is a Java / Spring Boot 3.4 port of a Python + LangChain prototype I built first. The Python version isn't included here.

## Why it's built this way

- **Local embeddings.** Documents are embedded inside the JVM with all-MiniLM-L6-v2 (quantized ONNX, via LangChain4j). The corpus is never sent to a third-party embedding API.
- **Grounded answers.** The LLM gets only the top-k retrieved passages and is told to answer from that context alone and to name the source file.
- **What does leave the machine:** the question and those top-k passages, sent to a Groq-hosted open-weight model (`openai/gpt-oss-20b`) to generate the answer. To keep everything local, swap `GroqChatClient` for a locally hosted model.

## How it works

```
data/ (md, txt, pdf, docx, xlsx, csv, json)
   → DocumentLoader        multi-format ingestion (PDFBox, POI, commons-csv)
   → EmbeddingPipeline     header-aware chunking for Markdown, h1–h3 (1000 chars, 200 overlap)
   → EmbeddingService      all-MiniLM-L6-v2, in-JVM ONNX
   → FaissVectorStore      flat L2 index persisted to vectors.bin + metadata.json
   → RagSearchService      top-k retrieval with a distance cutoff; Markdown hits expand to the full
                           parent document; context-only prompt
   → GroqChatClient        answer that names its source
```

`FaissVectorStore` is a hand-built flat L2 index that mirrors FAISS `IndexFlatL2`. It is not a FAISS binding.

## Run

Requirements: Java 17+ and a `GROQ_API_KEY` environment variable. The Gradle wrapper is included.

```bash
export GROQ_API_KEY=your_key_here
./gradlew bootRun                                          # builds the index from data/, runs a sample query, serves HTTP
./gradlew bootRun --args="--rag.run-query-on-startup=false" # HTTP only
```

## REST API

```bash
curl -s http://localhost:8080/api/search -H 'Content-Type: application/json' \
  -d '{"query":"Which Java version did we choose and why?","topK":3}'

curl -s -X POST http://localhost:8080/api/reindex   # rebuild the index from data/
```

## Sample data

`data/` contains illustrative sample ADRs and a short note, not real company documents. Point `rag.data-dir` at your own folder to try it on real ADRs.

## Status

This is a proof of concept. It hasn't been load-tested or production-hardened, there's no evaluation harness yet, and the flat index is O(n) per query, which is fine for hundreds of documents but not millions.

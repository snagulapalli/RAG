# RAG (Java + Spring Boot)

Spring Boot port of the Python RAG app (original sources kept under `python/`).

## What it does

| Python | Java |
|--------|------|
| `data_loader.py` | `DocumentLoader` |
| `embedding.py` | `EmbeddingPipeline` + `EmbeddingService` (all-MiniLM-L6-v2 via LangChain4j ONNX) |
| `vectorstore.py` (FAISS L2) | `FaissVectorStore` (flat L2 + `vectors.bin` / `metadata.json`) |
| `search.py` + Groq | `RagSearchService` + `GroqChatClient` |
| `app.py` CLI | `StartupQueryRunner` + REST API |

## Prerequisites

- Java 17+
- Gradle (wrapper included)
- `GROQ_API_KEY` environment variable
- Documents under `data/` (a sample markdown file is included)

## Run

```bash
export GROQ_API_KEY=your_key_here

# Build & run
./gradlew bootRun
# or:
./gradlew bootJar && java -jar build/libs/rag-0.0.1-SNAPSHOT.jar
```

Optional query args (same idea as `python app.py "..."`):

```bash
./gradlew bootRun --args="What is attention mechanism?"
```

Disable the startup query and only serve HTTP:

```bash
./gradlew bootRun --args="--rag.run-query-on-startup=false"
```

## REST API

```bash
# Search + summarize
curl -s http://localhost:8080/api/search \
  -H 'Content-Type: application/json' \
  -d '{"query":"What is attention mechanism?","topK":3}'

# Rebuild index from data/
curl -s -X POST http://localhost:8080/api/reindex
```

## Configuration

See `src/main/resources/application.yml`:

- `rag.data-dir` — document folder (default `data`)
- `rag.persist-dir` — vector index folder (default `faiss_store`)
- `rag.llm-model` — Groq model id
- `rag.groq-api-key` — from `GROQ_API_KEY`

## Python original

The previous Python implementation lives in `python/` and can still be run from that directory with its `.venv`.

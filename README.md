# RAG: privacy-preserving Q&A over Architecture Decision Records

A proof-of-concept retrieval-augmented generation (RAG) service. It answers natural-language questions about a folder of Architecture Decision Records (ADRs) and other documents, and every answer names the source it came from.

This repo is a Java / Spring Boot 3.4 port of a Python + LangChain prototype I built first. The Python version isn't included here.

## Why it's built this way

- **Local embeddings.** Documents are embedded inside the JVM with all-MiniLM-L6-v2 (quantized ONNX, via LangChain4j). The corpus is never sent to a third-party embedding API.
- **Grounded answers.** The LLM gets only the documents retrieved for the question and is told to answer from that context alone and to name the source file.
- **Local LLM by default.** `GroqChatClient` talks to a standard OpenAI-compatible chat-completions endpoint, so going fully local is a config change, not a code change. Out of the box it points at [Ollama](https://ollama.com) on `http://localhost:11434/v1` with a small model (`llama3.2:3b`), so the question and the retrieved passages never leave the machine either. The same client can be pointed back at Groq's hosted API; see [Switching back to Groq](#switching-back-to-groq).

## How it works

```
data/ (md, txt, pdf, docx, xlsx, csv, json)
   → DocumentLoader        multi-format ingestion (PDFBox, POI, commons-csv)
   → EmbeddingPipeline     header-aware chunking for Markdown, h1–h3 (1000 chars, 200 overlap)
   → EmbeddingService      all-MiniLM-L6-v2, in-JVM ONNX
   → FaissVectorStore      flat L2 index persisted to vectors.bin + metadata.json
   → RagSearchService      top-k retrieval with a distance cutoff; Markdown hits expand to the full
                           parent document; context-only prompt
   → GroqChatClient        OpenAI-compatible chat call (Ollama by default, Groq optional); answer names its source
```

`FaissVectorStore` is a hand-built flat L2 index that mirrors FAISS `IndexFlatL2`. It is not a FAISS binding.

## Run

Requirements: Java 17+ and a running [Ollama](https://ollama.com) server. The Gradle wrapper is included. No API key is needed for the local setup.

Pull the model once before the first run. On an Intel Mac a 20B model is slow, so the default is a 3B one:

```bash
ollama pull llama3.2:3b
```

Then build and run:

```bash
./gradlew bootRun                                          # builds the index from data/, runs a sample query, serves HTTP
./gradlew bootRun --args="--rag.run-query-on-startup=false" # HTTP only
```

### Configuration

LLM settings live in `src/main/resources/application.yml`:

| Property | Default | Notes |
|----------|---------|-------|
| `rag.groq-base-url` | `http://localhost:11434/v1` | Any OpenAI-compatible chat-completions endpoint |
| `rag.llm-model` | `llama3.2:3b` | Must be a model you have pulled (`ollama list`) |
| `rag.groq-api-key` | `${GROQ_API_KEY:ollama}` | Ollama ignores the value; the client only requires it to be non-empty |

To use a different local model, pull it and set `rag.llm-model` to its name, e.g. `--args="--rag.llm-model=qwen2.5:3b"`.

### Switching back to Groq

Point the base URL at Groq's hosted API and provide a real key. Either edit `application.yml`:

```yaml
rag:
  groq-base-url: https://api.groq.com/openai/v1
  llm-model: openai/gpt-oss-20b
  groq-api-key: ${GROQ_API_KEY:}
```

or override at launch without touching the file:

```bash
export GROQ_API_KEY=your_key_here
./gradlew bootRun --args="--rag.groq-base-url=https://api.groq.com/openai/v1 --rag.llm-model=openai/gpt-oss-20b"
```

With Groq, the question and the retrieved documents (full text for Markdown ADRs) are sent to Groq's servers to generate the answer. Document embedding and the vector index still run locally in either mode.

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

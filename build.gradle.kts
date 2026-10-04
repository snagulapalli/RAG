plugins {
    java
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"
description = "RAG search ported from Python to Spring Boot"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Local MiniLM embeddings (same model family as Python sentence-transformers)
    implementation("dev.langchain4j:langchain4j-embeddings-all-minilm-l6-v2-q:0.36.2")

    // Document loaders
    implementation("org.apache.pdfbox:pdfbox:3.0.4")
    implementation("org.apache.poi:poi-ooxml:5.3.0")
    implementation("org.apache.poi:poi-scratchpad:5.3.0")
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("org.apache.commons:commons-csv:1.12.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// DJL HuggingFace tokenizers 0.30.0+ ships no native library for Intel Macs (osx-x86_64).
// Pin 0.29.0 so in-JVM embeddings load on x86_64 macOS.
configurations.all {
    resolutionStrategy {
        force("ai.djl.huggingface:tokenizers:0.29.0")
    }
}
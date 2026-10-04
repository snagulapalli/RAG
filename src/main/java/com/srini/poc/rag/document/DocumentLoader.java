package com.srini.poc.rag.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Loads PDF, TXT, CSV, Excel, Word, JSON, and Markdown files — mirrors python/src/data_loader.py.
 */
@Service
public class DocumentLoader {

    private static final Logger log = LoggerFactory.getLogger(DocumentLoader.class);

    private final ObjectMapper objectMapper;

    public DocumentLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Document> loadAllDocuments(String dataDir) {
        Path dataPath = Path.of(dataDir).toAbsolutePath().normalize();
        log.info("[DEBUG] Data path: {}", dataPath);
        if (!Files.isDirectory(dataPath)) {
            log.warn("[WARN] Data directory does not exist: {}", dataPath);
            return List.of();
        }

        List<Document> documents = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dataPath)) {
            walk.filter(Files::isRegularFile).forEach(path -> {
                String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                try {
                    if (name.endsWith(".pdf")) {
                        documents.addAll(loadPdf(path));
                    } else if (name.endsWith(".txt") || name.endsWith(".md")) {
                        documents.addAll(loadText(path));
                    } else if (name.endsWith(".csv")) {
                        documents.addAll(loadCsv(path));
                    } else if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
                        documents.addAll(loadExcel(path));
                    } else if (name.endsWith(".docx")) {
                        documents.addAll(loadWord(path));
                    } else if (name.endsWith(".json")) {
                        documents.addAll(loadJson(path));
                    }
                } catch (Exception e) {
                    log.error("[ERROR] Failed to load {}: {}", path, e.getMessage());
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Failed to walk data directory: " + dataPath, e);
        }

        log.info("[DEBUG] Total loaded documents: {}", documents.size());
        return documents;
    }

    private List<Document> loadPdf(Path path) throws IOException {
        log.info("[DEBUG] Loading PDF: {}", path);
        List<Document> docs = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pages = pdf.getNumberOfPages();
            for (int i = 1; i <= pages; i++) {
                stripper.setStartPage(i);
                stripper.setEndPage(i);
                String text = stripper.getText(pdf);
                if (text == null || text.isBlank()) {
                    continue;
                }
                Map<String, String> meta = baseMeta(path);
                meta.put("page", String.valueOf(i - 1));
                meta.put("page_label", String.valueOf(i));
                docs.add(new Document(text, meta));
            }
        }
        log.info("[DEBUG] Loaded {} PDF docs from {}", docs.size(), path);
        return docs;
    }

    private List<Document> loadText(Path path) throws IOException {
        log.info("[DEBUG] Loading text: {}", path);
        String content = Files.readString(path, StandardCharsets.UTF_8);
        Document doc = new Document(content, baseMeta(path));
        log.info("[DEBUG] Loaded 1 text doc from {}", path);
        return List.of(doc);
    }

    private List<Document> loadCsv(Path path) throws IOException {
        log.info("[DEBUG] Loading CSV: {}", path);
        List<Document> docs = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader)) {
            int row = 0;
            for (CSVRecord currentRecord : parser) {
                StringBuilder sb = new StringBuilder();
                for (String header : parser.getHeaderNames()) {
                    sb.append(header).append(": ").append(currentRecord.get(header)).append('\n');
                }
                Map<String, String> meta = baseMeta(path);
                meta.put("row", String.valueOf(row++));
                docs.add(new Document(sb.toString().trim(), meta));
            }
        }
        log.info("[DEBUG] Loaded {} CSV docs from {}", docs.size(), path);
        return docs;
    }

    private List<Document> loadExcel(Path path) throws IOException {
        log.info("[DEBUG] Loading Excel: {}", path);
        List<Document> docs = new ArrayList<>();
        DataFormatter formatter = new DataFormatter();
        try (InputStream in = Files.newInputStream(path);
             Workbook workbook = WorkbookFactory.create(in)) {
            for (Sheet sheet : workbook) {
                StringBuilder sb = new StringBuilder();
                sb.append("Sheet: ").append(sheet.getSheetName()).append('\n');
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    for (Cell cell : row) {
                        cells.add(formatter.formatCellValue(cell));
                    }
                    if (!cells.isEmpty()) {
                        sb.append(String.join(" | ", cells)).append('\n');
                    }
                }
                Map<String, String> meta = baseMeta(path);
                meta.put("sheet", sheet.getSheetName());
                docs.add(new Document(sb.toString().trim(), meta));
            }
        }
        log.info("[DEBUG] Loaded {} Excel docs from {}", docs.size(), path);
        return docs;
    }

    private List<Document> loadWord(Path path) throws IOException {
        log.info("[DEBUG] Loading Word: {}", path);
        try (InputStream in = Files.newInputStream(path);
             XWPFDocument docx = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(docx)) {
            Document doc = new Document(extractor.getText(), baseMeta(path));
            log.info("[DEBUG] Loaded 1 Word doc from {}", path);
            return List.of(doc);
        }
    }

    private List<Document> loadJson(Path path) throws IOException {
        log.info("[DEBUG] Loading JSON: {}", path);
        JsonNode root = objectMapper.readTree(path.toFile());
        String content = root.isTextual() ? root.asText() : objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        Document doc = new Document(content, baseMeta(path));
        log.info("[DEBUG] Loaded 1 JSON doc from {}", path);
        return List.of(doc);
    }

    private Map<String, String> baseMeta(Path path) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("source", path.toAbsolutePath().normalize().toString());
        return meta;
    }
}

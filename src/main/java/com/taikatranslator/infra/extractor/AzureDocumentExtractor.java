package com.taikatranslator.infra.extractor;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taikatranslator.core.extractor.DocumentExtractor;
import com.taikatranslator.core.extractor.ExtractionException;
import com.taikatranslator.model.BoundingBox;
import com.taikatranslator.model.DocumentLayout;
import com.taikatranslator.model.Table;
import com.taikatranslator.model.TableCell;
import com.taikatranslator.model.TableRow;
import com.taikatranslator.model.TextBlock;
import com.taikatranslator.model.TextStyle;

public class AzureDocumentExtractor implements DocumentExtractor {
    private static final Logger log = LoggerFactory.getLogger(AzureDocumentExtractor.class);
    private final String endpoint;
    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client;
    private String documentBaseName = "document";

    public AzureDocumentExtractor(String endpoint, String apiKey) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public void setDocumentBaseName(String documentBaseName) {
        if (documentBaseName != null && !documentBaseName.trim().isEmpty()) {
            this.documentBaseName = documentBaseName;
        }
    }

    @Override
    public DocumentLayout extractLayout(File cleanPageImage) throws ExtractionException {
        log.info("Sending image to Azure Document Intelligence: {}", cleanPageImage.getName());
        
        try {
            byte[] fileBytes = Files.readAllBytes(cleanPageImage.toPath());
            
            // 1. Submit Analyze request
            String requestUrl = endpoint + "/formrecognizer/documentModels/prebuilt-layout:analyze?api-version=2023-07-31&features=styleFont";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(requestUrl))
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .header("Content-Type", "image/png")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(fileBytes))
                    .build();
            
            HttpResponse<Void> submitResponse = client.send(request, HttpResponse.BodyHandlers.discarding());
            log.debug("Submit response code: {}", submitResponse.statusCode());
            
            if (submitResponse.statusCode() != 202) {
                throw new ExtractionException("Azure submission failed with status code: " + submitResponse.statusCode());
            }

            // Get operation location URL to poll for results
            String operationLocation = submitResponse.headers().firstValue("Operation-Location")
                    .orElseThrow(() -> new ExtractionException("Missing Operation-Location header in Azure response"));
            
            log.info("Azure analysis started. Polling: {}", operationLocation);

            // 2. Poll for results
            JsonNode fullResponseNode = pollForResults(operationLocation);
            
            // Log the JSON response to a folder named 'log'
            try {
                File logDir = new File("log");
                if (!logDir.exists()) {
                    logDir.mkdirs();
                }
                String pageIdentifier = cleanPageImage.getParentFile() != null ? cleanPageImage.getParentFile().getName() : "page";
                File logFile = new File(logDir, documentBaseName + "_" + pageIdentifier + "_azure_response.json");
                log.info("Saving Azure JSON response to log file: {}", logFile.getAbsolutePath());
                mapper.writerWithDefaultPrettyPrinter().writeValue(logFile, fullResponseNode);
            } catch (Exception e) {
                log.error("Failed to save Azure JSON response to log folder", e);
            }
            
            log.info("Azure analysis succeeded. Parsing layout JSON...");
            
            return parseAzureJson(fullResponseNode.get("analyzeResult"));

        } catch (Exception e) {
            log.error("Azure Document Extraction failed", e);
            throw new ExtractionException("Azure Extraction Failure: " + e.getMessage(), e);
        }
    }

    private JsonNode pollForResults(String operationUrl) throws Exception {
        int maxPolls = 60; // 5 minutes max
        int pollIntervalMs = 5000;
        
        for (int i = 0; i < maxPolls; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(operationUrl))
                    .header("Ocp-Apim-Subscription-Key", apiKey)
                    .GET()
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ExtractionException("Azure polling status error: " + response.statusCode());
            }
            
            JsonNode node = mapper.readTree(response.body());
            String status = node.get("status").asText();
            log.debug("Polling attempt {}/{} - Current Status: {}", i + 1, maxPolls, status);
            
            if ("succeeded".equals(status)) {
                return node;
            } else if ("failed".equals(status)) {
                throw new ExtractionException("Azure OCR processing failed on server: " + node.get("error"));
            }
            
            Thread.sleep(pollIntervalMs);
        }
        
        throw new ExtractionException("Azure OCR timed out.");
    }

    private DocumentLayout parseAzureJson(JsonNode analyzeResult) throws Exception {
        // Parse page details
        JsonNode pages = analyzeResult.get("pages");
        if (pages == null || pages.size() == 0) {
            throw new ExtractionException("No pages returned by Azure Document Intelligence");
        }
        
        JsonNode page = pages.get(0); // Standard single page analysis
        double pageWidth = page.get("width").asDouble();
        double pageHeight = page.get("height").asDouble();
        
        DocumentLayout layout = new DocumentLayout(pageWidth, pageHeight);
        
        // Parse global styles into bold and italic BitSets
        java.util.BitSet boldOffsets = new java.util.BitSet();
        java.util.BitSet italicOffsets = new java.util.BitSet();
        
        JsonNode stylesNode = analyzeResult.get("styles");
        if (stylesNode != null) {
            for (JsonNode styleNode : stylesNode) {
                boolean isBold = styleNode.has("fontWeight") && "bold".equalsIgnoreCase(styleNode.get("fontWeight").asText());
                boolean isItalic = styleNode.has("fontStyle") && "italic".equalsIgnoreCase(styleNode.get("fontStyle").asText());
                
                if (isBold || isItalic) {
                    JsonNode spansNode = styleNode.get("spans");
                    if (spansNode != null) {
                        for (JsonNode span : spansNode) {
                            int offset = span.get("offset").asInt();
                            int length = span.get("length").asInt();
                            for (int idx = offset; idx < offset + length; idx++) {
                                if (idx >= 0) {
                                    if (isBold) boldOffsets.set(idx);
                                    if (isItalic) italicOffsets.set(idx);
                                }
                            }
                        }
                    }
                }
            }
        }

        // Parse paragraphs
        JsonNode paragraphs = analyzeResult.get("paragraphs");
        if (paragraphs != null) {
            int order = 0;
            for (JsonNode paraNode : paragraphs) {
                String text = paraNode.get("content").asText();
                
                // Parse bounding polygon and construct a normalized BoundingBox
                BoundingBox box = extractBoundingBox(paraNode.get("boundingRegions"), pageWidth, pageHeight);
                
                // Extract font style (Azure provides some metadata or default fallback)
                TextStyle style = extractTextStyle(paraNode);
                
                TextBlock textBlock = new TextBlock(text, box, style, order++);
                
                // Segment text into inline runs based on bold/italic bitsets
                int paraOffset = 0;
                if (paraNode.has("spans") && paraNode.get("spans").size() > 0) {
                    paraOffset = paraNode.get("spans").get(0).get("offset").asInt();
                }
                
                if (text.length() > 0) {
                    StringBuilder currentRunText = new StringBuilder();
                    boolean lastBold = boldOffsets.get(paraOffset);
                    boolean lastItalic = italicOffsets.get(paraOffset);
                    
                    for (int i = 0; i < text.length(); i++) {
                        int globalIdx = paraOffset + i;
                        boolean currentBold = boldOffsets.get(globalIdx);
                        boolean currentItalic = italicOffsets.get(globalIdx);
                        
                        if (currentBold != lastBold || currentItalic != lastItalic) {
                            if (currentRunText.length() > 0) {
                                textBlock.addInlineRun(new com.taikatranslator.model.InlineRun(
                                    currentRunText.toString(), lastBold, lastItalic));
                                currentRunText.setLength(0);
                            }
                            lastBold = currentBold;
                            lastItalic = currentItalic;
                        }
                        currentRunText.append(text.charAt(i));
                    }
                    
                    if (currentRunText.length() > 0) {
                        textBlock.addInlineRun(new com.taikatranslator.model.InlineRun(
                            currentRunText.toString(), lastBold, lastItalic));
                    }
                } else {
                    textBlock.addInlineRun(new com.taikatranslator.model.InlineRun("", false, false));
                }
                
                layout.addTextBlock(textBlock);
            }
        }
        
        // Parse tables
        JsonNode tables = analyzeResult.get("tables");
        if (tables != null) {
            for (JsonNode tableNode : tables) {
                BoundingBox tableBox = extractBoundingBox(tableNode.get("boundingRegions"), pageWidth, pageHeight);
                
                Table table = new Table();
                table.setBoundingBox(tableBox);
                
                // Group cells by rowIndex
                Map<Integer, TableRow> rowMap = new HashMap<>();
                JsonNode cells = tableNode.get("cells");
                
                if (cells != null) {
                    for (JsonNode cellNode : cells) {
                        String text = cellNode.get("content").asText();
                        int rowIndex = cellNode.get("rowIndex").asInt();
                        int colIndex = cellNode.get("columnIndex").asInt();
                        int rowSpan = cellNode.has("rowSpan") ? cellNode.get("rowSpan").asInt() : 1;
                        int colSpan = cellNode.has("colSpan") ? cellNode.get("colSpan").asInt() : 1;
                        
                        BoundingBox cellBox = extractBoundingBox(cellNode.get("boundingRegions"), pageWidth, pageHeight);
                        TableCell cell = new TableCell(text, cellBox, rowIndex, colIndex, rowSpan, colSpan);
                        
                        TableRow row = rowMap.computeIfAbsent(rowIndex, k -> new TableRow());
                        row.addCell(cell);
                    }
                }
                
                // Add rows in sorted order of rowIndex
                int maxRow = rowMap.keySet().stream().max(Integer::compare).orElse(-1);
                for (int i = 0; i <= maxRow; i++) {
                    TableRow r = rowMap.get(i);
                    if (r != null) {
                        table.addRow(r);
                    }
                }
                
                layout.addTable(table);
            }
        }
        
        return layout;
    }

    private BoundingBox extractBoundingBox(JsonNode boundingRegions, double pageWidth, double pageHeight) {
        if (boundingRegions == null || boundingRegions.size() == 0) {
            return new BoundingBox(0.0, 0.0, 1.0, 1.0); // Default fullscreen
        }
        
        JsonNode region = boundingRegions.get(0);
        JsonNode polygon = region.get("polygon");
        if (polygon == null || polygon.size() < 8) {
            return new BoundingBox(0.0, 0.0, 1.0, 1.0);
        }
        
        // Polygon is [x1, y1, x2, y2, x3, y3, x4, y4]
        double minX = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = Double.MIN_VALUE;
        
        for (int i = 0; i < polygon.size(); i += 2) {
            double x = polygon.get(i).asDouble();
            double y = polygon.get(i + 1).asDouble();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        
        // Normalize coordinates relative to page size
        double normLeft = minX / pageWidth;
        double normTop = minY / pageHeight;
        double normWidth = (maxX - minX) / pageWidth;
        double normHeight = (maxY - minY) / pageHeight;
        
        return new BoundingBox(normTop, normLeft, normWidth, normHeight);
    }

    private TextStyle extractTextStyle(JsonNode paraNode) {
        // Build style from azure hints or fallback to Calibri 11pt
        TextStyle style = new TextStyle();
        style.setFontName("Calibri");
        style.setFontSize(11.0);
        
        if (paraNode.has("spans") && paraNode.get("spans").size() > 0) {
            // Azure can classify paragraphs (e.g. title, sectionHeading, etc.)
            String role = paraNode.has("role") ? paraNode.get("role").asText() : "";
            if ("title".equalsIgnoreCase(role)) {
                style.setFontSize(24.0);
                style.setBold(true);
            } else if ("sectionHeading".equalsIgnoreCase(role)) {
                style.setFontSize(16.0);
                style.setBold(true);
            } else if ("sectionSubheading".equalsIgnoreCase(role)) {
                style.setFontSize(13.0);
                style.setBold(true);
                style.setItalic(true);
            }
        }
        
        return style;
    }
}

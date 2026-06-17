package com.udocmachine.core.pipeline;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.udocmachine.core.assembler.WordAssembler;
import com.udocmachine.core.extractor.DocumentExtractor;
import com.udocmachine.core.translator.TextTranslator;
import com.udocmachine.core.vision.VisionProcessor;
import com.udocmachine.core.vision.VisionResult;
import com.udocmachine.infra.assembler.DocxWordAssembler;
import com.udocmachine.infra.extractor.AzureDocumentExtractor;
import com.udocmachine.infra.translator.DeepLTranslator;
import com.udocmachine.infra.vision.ProcessBuilderVisionProcessor;
import com.udocmachine.model.DocumentLayout;
import com.udocmachine.model.TextBlock;

public class TranslationPipeline {
    private static final Logger log = LoggerFactory.getLogger(TranslationPipeline.class);

    public static class PipelineResult {
        private final File englishDocx;
        private final File spanishDocx;

        public PipelineResult(File englishDocx, File spanishDocx) {
            this.englishDocx = englishDocx;
            this.spanishDocx = spanishDocx;
        }

        public File getEnglishDocx() {
            return englishDocx;
        }

        public File getSpanishDocx() {
            return spanishDocx;
        }
    }

    public static PipelineResult run(
            File inputPdf, 
            File outputDir, 
            String azureEndpoint, 
            String azureKey, 
            String deeplKey, 
            String deeplEndpoint, 
            String pythonExe, 
            String scriptPath) throws Exception {

        if (pythonExe == null || pythonExe.trim().isEmpty()) {
            pythonExe = "python";
        }
        if (scriptPath == null || scriptPath.trim().isEmpty()) {
            scriptPath = "vision/segmenter.py";
        }
        if (deeplEndpoint == null || deeplEndpoint.trim().isEmpty()) {
            deeplEndpoint = "https://api-free.deepl.com";
        }

        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        // Check if API keys are set, fallback to mock logs if missing for demonstration/testing
        boolean hasAzure = (azureEndpoint != null && !azureEndpoint.trim().isEmpty() && azureKey != null && !azureKey.trim().isEmpty());
        boolean hasDeepL = (deeplKey != null && !deeplKey.trim().isEmpty());

        if (!hasAzure) {
            log.warn("AZURE_ENDPOINT or AZURE_KEY not set. Extraction will use placeholder stubs.");
        }
        if (!hasDeepL) {
            log.warn("DEEPL_KEY not set. Translation will use placeholder stubs.");
        }

        // 2. Load/Convert Input to Page Images
        List<File> pageImages;
        String inputName = inputPdf.getName().toLowerCase();
        if (inputName.endsWith(".png") || inputName.endsWith(".jpg") || inputName.endsWith(".jpeg")) {
            log.info("Input file is a direct image. Copying into pipeline...");
            pageImages = new ArrayList<>();
            File pageImg = new File(outputDir, "page_1.png");
            if (pageImg.exists()) {
                pageImg.delete();
            }
            Files.copy(inputPdf.toPath(), pageImg.toPath());
            pageImages.add(pageImg);
        } else {
            log.info("Rendering PDF pages to high-resolution PNG images...");
            pageImages = renderPdfPages(inputPdf, outputDir);
        }
        log.info("Loaded {} page image(s) successfully.", pageImages.size());

        String baseName = inputPdf.getName();
        int dotIdx = baseName.lastIndexOf('.');
        if (dotIdx > 0) {
            baseName = baseName.substring(0, dotIdx);
        }

        // 3. Initialize Pipeline Components
        VisionProcessor visionProcessor = new ProcessBuilderVisionProcessor(pythonExe, scriptPath);
        DocumentExtractor extractor = hasAzure ? 
                new AzureDocumentExtractor(azureEndpoint, azureKey) : 
                new MockDocumentExtractor(baseName);
        
        if (extractor instanceof AzureDocumentExtractor) {
            ((AzureDocumentExtractor) extractor).setDocumentBaseName(baseName);
        }
        
        TextTranslator translator = hasDeepL ? 
                new DeepLTranslator(deeplKey, deeplEndpoint, 5, 1000) : 
                new MockTextTranslator();
        
        WordAssembler assembler = new DocxWordAssembler();

        // 4. Run page-by-page pipeline
        List<DocumentLayout> originalLayouts = new ArrayList<>();
        List<DocumentLayout> translatedLayouts = new ArrayList<>();
        List<VisionResult> visionResults = new ArrayList<>();

        int pageNum = 1;
        for (File pageImg : pageImages) {
            log.info("----- Processing Page {}/{} -----", pageNum, pageImages.size());
            File pageOutputDir = new File(outputDir, "page_" + pageNum);
            pageOutputDir.mkdirs();

            // A. Run Vision Helper
            VisionResult visionResult = visionProcessor.processPage(pageImg, pageOutputDir);
            visionResults.add(visionResult);
            
            // B. Run Structured Extraction on cleaned image
            DocumentLayout originalLayout = extractor.extractLayout(visionResult.getCleanImage());
            originalLayouts.add(originalLayout);

            // C. Run Translation
            List<String> engTexts = new ArrayList<>();
            for (TextBlock tb : originalLayout.getTextBlocks()) {
                engTexts.add(buildXmlForBlock(tb));
            }
            
            log.info("Translating {} text blocks...", engTexts.size());
            List<String> esTexts = translator.translate(engTexts);

            // D. Build Translated Layout model (Spanish)
            DocumentLayout translatedLayout = cloneLayoutWithSpanish(originalLayout, esTexts);
            translatedLayouts.add(translatedLayout);

            pageNum++;
        }

        // 5. Reconstruct Unified Multi-Page Documents
        log.info("Assembling final unified multi-page English document...");
        File engDocx = new File(outputDir, baseName + "_reconstructed.docx");
        assembler.assemble(originalLayouts, visionResults, engDocx);
        log.info("Generated English DOCX: {}", engDocx.getAbsolutePath());

        log.info("Assembling final unified multi-page Spanish document...");
        File esDocx = new File(outputDir, baseName + "_translated.docx");
        assembler.assemble(translatedLayouts, visionResults, esDocx);
        log.info("Generated Spanish DOCX: {}", esDocx.getAbsolutePath());

        return new PipelineResult(engDocx, esDocx);
    }

    private static List<File> renderPdfPages(File pdfFile, File outputDir) throws IOException {
        List<File> images = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); ++page) {
                // Render at 300 DPI for high fidelity
                java.awt.image.BufferedImage bim = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
                File pageFile = new File(outputDir, "page_" + (page + 1) + ".png");
                javax.imageio.ImageIO.write(bim, "PNG", pageFile);
                images.add(pageFile);
            }
        }
        return images;
    }

    private static DocumentLayout cloneLayoutWithSpanish(DocumentLayout original, List<String> translations) {
        DocumentLayout trans = new DocumentLayout(original.getPageWidth(), original.getPageHeight());
        trans.setTables(original.getTables()); // Tables structures remain identical
        
        List<TextBlock> originalBlocks = original.getTextBlocks();
        for (int i = 0; i < originalBlocks.size(); i++) {
            TextBlock origBlock = originalBlocks.get(i);
            String transXmlText = origBlock.getText();
            if (i < translations.size()) {
                transXmlText = translations.get(i);
            }
            
            List<com.udocmachine.model.InlineRun> parsedRuns = parseStyledText(transXmlText);
            
            // Build the clean text (without tags)
            StringBuilder cleanText = new StringBuilder();
            for (com.udocmachine.model.InlineRun r : parsedRuns) {
                cleanText.append(r.getText());
            }
            
            TextBlock transBlock = new TextBlock(
                    cleanText.toString(), 
                    origBlock.getBoundingBox(), 
                    origBlock.getTextStyle(), 
                    origBlock.getReadingOrder()
            );
            
            for (com.udocmachine.model.InlineRun r : parsedRuns) {
                transBlock.addInlineRun(r);
            }
            
            trans.addTextBlock(transBlock);
        }
        return trans;
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }

    private static String unescapeXml(String text) {
        if (text == null) return "";
        return text.replace("&amp;", "&")
                   .replace("&lt;", "<")
                   .replace("&gt;", ">")
                   .replace("&quot;", "\"")
                   .replace("&apos;", "'");
    }

    private static String buildXmlForBlock(TextBlock tb) {
        List<com.udocmachine.model.InlineRun> runs = tb.getInlineRuns();
        if (runs == null || runs.isEmpty()) {
            return escapeXml(tb.getText());
        }
        StringBuilder sb = new StringBuilder();
        for (com.udocmachine.model.InlineRun run : runs) {
            String runText = run.getText();
            if (runText == null) continue;
            
            if (run.isBold() && run.isItalic()) {
                sb.append("<b><i>").append(escapeXml(runText)).append("</i></b>");
            } else if (run.isBold()) {
                sb.append("<b>").append(escapeXml(runText)).append("</b>");
            } else if (run.isItalic()) {
                sb.append("<i>").append(escapeXml(runText)).append("</i>");
            } else {
                sb.append(escapeXml(runText));
            }
        }
        return sb.toString();
    }

    private static List<com.udocmachine.model.InlineRun> parseStyledText(String xmlText) {
        List<com.udocmachine.model.InlineRun> runs = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        boolean bold = false;
        boolean italic = false;
        
        int i = 0;
        while (i < xmlText.length()) {
            if (xmlText.startsWith("<b>", i)) {
                if (currentText.length() > 0) {
                    runs.add(new com.udocmachine.model.InlineRun(unescapeXml(currentText.toString()), bold, italic));
                    currentText.setLength(0);
                }
                bold = true;
                i += 3;
            } else if (xmlText.startsWith("</b>", i)) {
                if (currentText.length() > 0) {
                    runs.add(new com.udocmachine.model.InlineRun(unescapeXml(currentText.toString()), bold, italic));
                    currentText.setLength(0);
                }
                bold = false;
                i += 4;
            } else if (xmlText.startsWith("<i>", i)) {
                if (currentText.length() > 0) {
                    runs.add(new com.udocmachine.model.InlineRun(unescapeXml(currentText.toString()), bold, italic));
                    currentText.setLength(0);
                }
                italic = true;
                i += 3;
            } else if (xmlText.startsWith("</i>", i)) {
                if (currentText.length() > 0) {
                    runs.add(new com.udocmachine.model.InlineRun(unescapeXml(currentText.toString()), bold, italic));
                    currentText.setLength(0);
                }
                italic = false;
                i += 4;
            } else {
                currentText.append(xmlText.charAt(i));
                i++;
            }
        }
        if (currentText.length() > 0) {
            runs.add(new com.udocmachine.model.InlineRun(unescapeXml(currentText.toString()), bold, italic));
        }
        return runs;
    }

    // --- Mock Fallbacks for testing without actual cloud API keys ---
    private static class MockDocumentExtractor implements DocumentExtractor {
        private String documentBaseName = "document";

        public MockDocumentExtractor(String documentBaseName) {
            this.documentBaseName = documentBaseName;
        }

        public void setDocumentBaseName(String documentBaseName) {
            if (documentBaseName != null && !documentBaseName.trim().isEmpty()) {
                this.documentBaseName = documentBaseName;
            }
        }

        @Override
        public DocumentLayout extractLayout(File cleanPageImage) {
            log.warn("[MOCK] Azure Extractor mock triggered. Generating sample layout.");
            
            // Log a mock JSON response to a folder named 'log'
            try {
                File logDir = new File("log");
                if (!logDir.exists()) {
                    logDir.mkdirs();
                }
                String pageIdentifier = cleanPageImage.getParentFile() != null ? cleanPageImage.getParentFile().getName() : "page";
                File logFile = new File(logDir, documentBaseName + "_" + pageIdentifier + "_azure_response.json");
                log.info("Saving MOCK Azure JSON response to log file: {}", logFile.getAbsolutePath());
                
                String mockJson = "{\n" +
                        "  \"status\": \"succeeded\",\n" +
                        "  \"analyzeResult\": {\n" +
                        "    \"apiVersion\": \"2023-07-31\",\n" +
                        "    \"modelId\": \"prebuilt-layout\",\n" +
                        "    \"stringIndexType\": \"textElements\",\n" +
                        "    \"content\": \"OFFICIAL DOCUMENT CERTIFICATE\\nThis certifies that the digital artifacts generated inside this workspace comply with high-fidelity formatting standards. The system automatically handles signatures and overlapping colored stamps.\",\n" +
                        "    \"pages\": [\n" +
                        "      {\n" +
                        "        \"pageNumber\": 1,\n" +
                        "        \"angle\": 0.0,\n" +
                        "        \"width\": 8.5,\n" +
                        "        \"height\": 11.0,\n" +
                        "        \"unit\": \"inch\",\n" +
                        "        \"words\": [],\n" +
                        "        \"lines\": [],\n" +
                        "        \"spans\": [\n" +
                        "          { \"offset\": 0, \"length\": 204 }\n" +
                        "        ]\n" +
                        "      }\n" +
                        "    ],\n" +
                        "    \"paragraphs\": [\n" +
                        "      {\n" +
                        "        \"content\": \"OFFICIAL DOCUMENT CERTIFICATE\",\n" +
                        "        \"boundingRegions\": [\n" +
                        "          {\n" +
                        "            \"pageNumber\": 1,\n" +
                        "            \"polygon\": [ 1.7, 0.55, 6.8, 0.55, 6.8, 1.1, 1.7, 1.1 ]\n" +
                        "          }\n" +
                        "        ],\n" +
                        "        \"spans\": [\n" +
                        "          { \"offset\": 0, \"length\": 29 }\n" +
                        "        ],\n" +
                        "        \"role\": \"title\"\n" +
                        "      },\n" +
                        "      {\n" +
                        "        \"content\": \"This certifies that the digital artifacts generated inside this workspace comply with high-fidelity formatting standards. The system automatically handles signatures and overlapping colored stamps.\",\n" +
                        "        \"boundingRegions\": [\n" +
                        "          {\n" +
                        "            \"pageNumber\": 1,\n" +
                        "            \"polygon\": [ 0.85, 1.65, 7.65, 1.65, 7.65, 2.75, 0.85, 2.75 ]\n" +
                        "          }\n" +
                        "        ],\n" +
                        "        \"spans\": [\n" +
                        "          { \"offset\": 30, \"length\": 174 }\n" +
                        "        ]\n" +
                        "      }\n" +
                        "    ],\n" +
                        "    \"tables\": [],\n" +
                        "    \"styles\": []\n" +
                        "  }\n" +
                        "}";
                
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(mockJson);
                mapper.writerWithDefaultPrettyPrinter().writeValue(logFile, node);
            } catch (Exception e) {
                log.error("Failed to save MOCK Azure JSON response to log folder", e);
            }

            DocumentLayout l = new DocumentLayout(8.5 * 300, 11 * 300);
            
            // Generate some sample blocks
            l.addTextBlock(new TextBlock(
                    "OFFICIAL DOCUMENT CERTIFICATE", 
                    new com.udocmachine.model.BoundingBox(0.05, 0.2, 0.6, 0.05), 
                    new com.udocmachine.model.TextStyle("Arial", 20, true, false, "#000000"), 
                    0
            ));
            l.addTextBlock(new TextBlock(
                    "This certifies that the digital artifacts generated inside this workspace comply with high-fidelity formatting standards. The system automatically handles signatures and overlapping colored stamps.", 
                    new com.udocmachine.model.BoundingBox(0.15, 0.1, 0.8, 0.1), 
                    new com.udocmachine.model.TextStyle("Calibri", 11, false, false, "#333333"), 
                    1
            ));
            return l;
        }
    }

    private static class MockTextTranslator implements TextTranslator {
        @Override
        public List<String> translate(List<String> textBlocks) {
            log.warn("[MOCK] DeepL Translator mock triggered.");
            List<String> out = new ArrayList<>();
            for (String block : textBlocks) {
                if (block.contains("CERTIFICATE")) {
                    out.add("CERTIFICADO DE DOCUMENTO OFICIAL");
                } else if (block.contains("This certifies")) {
                    out.add("Esto certifica que los artefactos digitales generados dentro de este espacio de trabajo cumplen con estándares de formato de alta fidelidad. El sistema maneja automáticamente firmas y sellos de colores superpuestos.");
                } else {
                    out.add("[ES] " + block);
                }
            }
            return out;
        }
    }
}

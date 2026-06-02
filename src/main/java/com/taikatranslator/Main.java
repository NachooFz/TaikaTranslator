package com.taikatranslator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.taikatranslator.core.assembler.WordAssembler;
import com.taikatranslator.core.extractor.DocumentExtractor;
import com.taikatranslator.core.translator.TextTranslator;
import com.taikatranslator.core.vision.VisionProcessor;
import com.taikatranslator.core.vision.VisionResult;
import com.taikatranslator.infra.assembler.DocxWordAssembler;
import com.taikatranslator.infra.extractor.AzureDocumentExtractor;
import com.taikatranslator.infra.translator.DeepLTranslator;
import com.taikatranslator.infra.vision.ProcessBuilderVisionProcessor;
import com.taikatranslator.model.DocumentLayout;
import com.taikatranslator.model.TextBlock;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        log.info("=================================================");
        log.info("Starting TaikaTranslator Pipeline Execution");
        log.info("=================================================");

        long startTime = System.currentTimeMillis();

        // Load env file if present
        Map<String, String> envFileMap = loadEnvFile(new File(".env"));

        // Check if we should run in server mode (default unless --input is specified)
        boolean runServer = true;
        for (String arg : args) {
            if ("--input".equals(arg)) {
                runServer = false;
                break;
            }
        }
        for (String arg : args) {
            if ("--server".equals(arg)) {
                runServer = true;
                break;
            }
        }

        if (runServer) {
            int port = 8080;
            String portEnv = getEnvOrConfig("PORT", envFileMap);
            if (portEnv != null) {
                try {
                    port = Integer.parseInt(portEnv);
                } catch (NumberFormatException e) {
                    log.warn("Invalid PORT env value: {}, using 8080", portEnv);
                }
            }
            log.info("Starting in Web Server Mode on port {}...", port);
            com.taikatranslator.infra.server.TranslationServer.start(port);
            return;
        }

        // 1. Parsing Arguments
        String inputPdfPath = getEnvOrConfig("INPUT_PDF", envFileMap);
        String outputDirStr = getEnvOrConfig("OUTPUT_DIR", envFileMap);
        
        // Configuration fallbacks (Env -> defaults)
        String azureEndpoint = getEnvOrConfig("AZURE_ENDPOINT", envFileMap);
        String azureKey = getEnvOrConfig("AZURE_KEY", envFileMap);
        String deeplKey = getEnvOrConfig("DEEPL_KEY", envFileMap);
        String deeplEndpoint = getEnvOrConfig("DEEPL_ENDPOINT", envFileMap);
        if (deeplEndpoint == null) {
            deeplEndpoint = "https://api-free.deepl.com"; // Standard default
        }
        
        String pythonExe = getEnvOrConfig("PYTHON_EXE", envFileMap);
        if (pythonExe == null) {
            pythonExe = "C:\\Users\\Nacho\\AppData\\Local\\Programs\\Python\\Python312\\python.exe";
        }
        String scriptPath = "vision/segmenter.py";

        for (int i = 0; i < args.length; i++) {
            if ("--input".equals(args[i]) && i + 1 < args.length) {
                inputPdfPath = args[++i];
            } else if ("--output-dir".equals(args[i]) && i + 1 < args.length) {
                outputDirStr = args[++i];
            } else if ("--azure-endpoint".equals(args[i]) && i + 1 < args.length) {
                azureEndpoint = args[++i];
            } else if ("--azure-key".equals(args[i]) && i + 1 < args.length) {
                azureKey = args[++i];
            } else if ("--deepl-key".equals(args[i]) && i + 1 < args.length) {
                deeplKey = args[++i];
            } else if ("--python-exe".equals(args[i]) && i + 1 < args.length) {
                pythonExe = args[++i];
            }
        }

        // Validate Prereq Configs
        if (inputPdfPath == null || outputDirStr == null) {
            log.error("Missing mandatory arguments. Usage: java Main --input <path.pdf|image> --output-dir <path>");
            log.error("Optional arguments: --azure-endpoint <url> --azure-key <key> --deepl-key <key> --python-exe <path>");
            System.exit(1);
        }

        File inputPdf = new File(inputPdfPath);
        File outputDir = new File(outputDirStr);

        if (!inputPdf.exists()) {
            log.error("Input file not found: {}", inputPdf.getAbsolutePath());
            System.exit(1);
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

        try {
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
                    new MockDocumentExtractor();
            
            if (extractor instanceof AzureDocumentExtractor) {
                ((AzureDocumentExtractor) extractor).setDocumentBaseName(baseName);
            } else if (extractor instanceof MockDocumentExtractor) {
                ((MockDocumentExtractor) extractor).setDocumentBaseName(baseName);
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

            long duration = System.currentTimeMillis() - startTime;
            log.info("=================================================");
            log.info("SUCCESS: E2E document translation pipeline finished.");
            log.info("Total processing duration: {}ms", duration);
            log.info("=================================================");

        } catch (Exception e) {
            log.error("Pipeline crashed during execution", e);
            System.exit(1);
        }
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
            
            List<com.taikatranslator.model.InlineRun> parsedRuns = parseStyledText(transXmlText);
            
            // Build the clean text (without tags)
            StringBuilder cleanText = new StringBuilder();
            for (com.taikatranslator.model.InlineRun r : parsedRuns) {
                cleanText.append(r.getText());
            }
            
            TextBlock transBlock = new TextBlock(
                    cleanText.toString(), 
                    origBlock.getBoundingBox(), 
                    origBlock.getTextStyle(), 
                    origBlock.getReadingOrder()
            );
            
            for (com.taikatranslator.model.InlineRun r : parsedRuns) {
                transBlock.addInlineRun(r);
            }
            
            trans.addTextBlock(transBlock);
        }
        return trans;
    }

    // --- Mock Fallbacks for testing without actual cloud API keys ---
    private static class MockDocumentExtractor implements DocumentExtractor {
        private String documentBaseName = "document";

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
                    new com.taikatranslator.model.BoundingBox(0.05, 0.2, 0.6, 0.05), 
                    new com.taikatranslator.model.TextStyle("Arial", 20, true, false, "#000000"), 
                    0
            ));
            l.addTextBlock(new TextBlock(
                    "This certifies that the digital artifacts generated inside this workspace comply with high-fidelity formatting standards. The system automatically handles signatures and overlapping colored stamps.", 
                    new com.taikatranslator.model.BoundingBox(0.15, 0.1, 0.8, 0.1), 
                    new com.taikatranslator.model.TextStyle("Calibri", 11, false, false, "#333333"), 
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

    private static Map<String, String> loadEnvFile(File envFile) {
        Map<String, String> env = new HashMap<>();
        if (!envFile.exists()) return env;
        try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eqIdx = line.indexOf('=');
                if (eqIdx > 0) {
                    String key = line.substring(0, eqIdx).trim();
                    String value = line.substring(eqIdx + 1).trim();
                    if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    } else if (value.startsWith("'") && value.endsWith("'") && value.length() >= 2) {
                        value = value.substring(1, value.length() - 1);
                    }
                    env.put(key, value);
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read .env file: {}", e.getMessage());
        }
        return env;
    }

    private static String getEnvOrConfig(String key, Map<String, String> envFileMap) {
        String sysVal = System.getenv(key);
        if (sysVal != null && !sysVal.isEmpty()) {
            return sysVal;
        }
        return envFileMap.get(key);
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
        List<com.taikatranslator.model.InlineRun> runs = tb.getInlineRuns();
        if (runs == null || runs.isEmpty()) {
            return escapeXml(tb.getText());
        }
        StringBuilder sb = new StringBuilder();
        for (com.taikatranslator.model.InlineRun run : runs) {
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

    private static List<com.taikatranslator.model.InlineRun> parseStyledText(String xmlText) {
        List<com.taikatranslator.model.InlineRun> runs = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        boolean bold = false;
        boolean italic = false;
        
        int i = 0;
        while (i < xmlText.length()) {
            if (xmlText.startsWith("<b>", i)) {
                if (currentText.length() > 0) {
                    runs.add(new com.taikatranslator.model.InlineRun(unescapeXml(currentText.toString()), bold, italic));
                    currentText.setLength(0);
                }
                bold = true;
                i += 3;
            } else if (xmlText.startsWith("</b>", i)) {
                if (currentText.length() > 0) {
                    runs.add(new com.taikatranslator.model.InlineRun(unescapeXml(currentText.toString()), bold, italic));
                    currentText.setLength(0);
                }
                bold = false;
                i += 4;
            } else if (xmlText.startsWith("<i>", i)) {
                if (currentText.length() > 0) {
                    runs.add(new com.taikatranslator.model.InlineRun(unescapeXml(currentText.toString()), bold, italic));
                    currentText.setLength(0);
                }
                italic = true;
                i += 3;
            } else if (xmlText.startsWith("</i>", i)) {
                if (currentText.length() > 0) {
                    runs.add(new com.taikatranslator.model.InlineRun(unescapeXml(currentText.toString()), bold, italic));
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
            runs.add(new com.taikatranslator.model.InlineRun(unescapeXml(currentText.toString()), bold, italic));
        }
        return runs;
    }
}

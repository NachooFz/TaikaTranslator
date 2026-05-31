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
            log.error("Missing mandatory arguments. Usage: java Main --input <path.pdf> --output-dir <path>");
            log.error("Optional arguments: --azure-endpoint <url> --azure-key <key> --deepl-key <key> --python-exe <path>");
            System.exit(1);
        }

        File inputPdf = new File(inputPdfPath);
        File outputDir = new File(outputDirStr);

        if (!inputPdf.exists()) {
            log.error("Input PDF file not found: {}", inputPdf.getAbsolutePath());
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
            // 2. Convert PDF to Page Images (using PDFBox)
            log.info("Rendering PDF pages to high-resolution PNG images...");
            List<File> pageImages = renderPdfPages(inputPdf, outputDir);
            log.info("Rendered {} page(s) successfully.", pageImages.size());

            // 3. Initialize Pipeline Components
            VisionProcessor visionProcessor = new ProcessBuilderVisionProcessor(pythonExe, scriptPath);
            DocumentExtractor extractor = hasAzure ? 
                    new AzureDocumentExtractor(azureEndpoint, azureKey) : 
                    new MockDocumentExtractor();
            
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
                    engTexts.add(tb.getText());
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
            File engDocx = new File(outputDir, inputPdf.getName().replace(".pdf", "") + "_reconstructed.docx");
            assembler.assemble(originalLayouts, visionResults, engDocx);
            log.info("Generated English DOCX: {}", engDocx.getAbsolutePath());

            log.info("Assembling final unified multi-page Spanish document...");
            File esDocx = new File(outputDir, inputPdf.getName().replace(".pdf", "") + "_translated.docx");
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
            String transText = origBlock.getText();
            if (i < translations.size()) {
                transText = translations.get(i);
            }
            
            TextBlock transBlock = new TextBlock(
                    transText, 
                    origBlock.getBoundingBox(), 
                    origBlock.getTextStyle(), 
                    origBlock.getReadingOrder()
            );
            trans.addTextBlock(transBlock);
        }
        return trans;
    }

    // --- Mock Fallbacks for testing without actual cloud API keys ---
    private static class MockDocumentExtractor implements DocumentExtractor {
        @Override
        public DocumentLayout extractLayout(File cleanPageImage) {
            log.warn("[MOCK] Azure Extractor mock triggered. Generating sample layout.");
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
}

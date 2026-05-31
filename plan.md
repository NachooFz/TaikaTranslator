# Implementation Plan: Document Extraction & Translation Pipeline (TaikaTranslator)

This implementation plan outlines the step-by-step technical architecture, directory structure, core interfaces, libraries, and verification strategies for the document extraction and translation pipeline.

---

## 1. System Architecture & Interfaces

The system will follow a clean, interface-driven design using pure Java 17/21 (decoupled from any heavyweight framework) and a Python vision pre-processing module.

### A. Java Orchestrator Interfaces

#### 1. `VisionProcessor`
Responsible for invoking the Python helper to extract stamps/seals/signatures and return a cleaned image.
```java
package com.taikatranslator.core.vision;

import java.io.File;
import java.util.List;

public interface VisionProcessor {
    /**
     * Preprocesses page images to segment visual artifacts and generate masked, clean pages.
     * @param sourcePageImage The high-res page image.
     * @param outputDirectory The directory to save cropped artifacts.
     * @return VisionResult containing paths to cropped artifacts and the clean page image.
     */
    VisionResult processPage(File sourcePageImage, File outputDirectory) throws VisionException;
}
```

#### 2. `DocumentExtractor`
Interacts with the Cloud OCR API (Azure Document Intelligence or Google Cloud Document AI) to extract paragraphs, tables, reading orders, and bounding box metadata from clean images.
```java
package com.taikatranslator.core.extractor;

import java.io.File;
import com.taikatranslator.model.DocumentLayout;

public interface DocumentExtractor {
    /**
     * Extracts structural layouts and metadata from the cleaned image.
     * @param cleanPageImage The masked page image with visual artifacts removed.
     * @return A parsed DocumentLayout model containing paragraph coordinates, fonts, tables, etc.
     */
    DocumentLayout extractLayout(File cleanPageImage) throws ExtractionException;
}
```

#### 3. `TextTranslator`
Handles text-block translation while maintaining formatting and structural mappings.
```java
package com.taikatranslator.core.translator;

import java.util.List;

public interface TextTranslator {
    /**
     * Translates a list of text blocks from source language (English) to target language (Spanish).
     * @param textBlocks The list of text blocks.
     * @return The translated list of text blocks in the same order.
     */
    List<String> translate(List<String> textBlocks) throws TranslationException;
}
```

#### 4. `WordAssembler`
Assembles the final `.docx` using absolute bounding boxes, styled paragraphs, tables, and overlays the segmented stamps/signatures.
```java
package com.taikatranslator.core.assembler;

import java.io.File;
import com.taikatranslator.model.DocumentLayout;
import com.taikatranslator.core.vision.VisionResult;

public interface WordAssembler {
    /**
     * Generates a .docx representation based on layout, translated text, and visual artifacts.
     * @param layout The structural document layout.
     * @param visionResult The visual artifacts extracted from the page.
     * @param outputFile The file path where the .docx should be written.
     */
    void assemble(DocumentLayout layout, VisionResult visionResult, File outputFile) throws AssemblyException;
}
```

---

## 2. Directory Structure & Layout

We will organize the repository using standard Maven structure for Java and a dedicated vision subdirectory for the Python modules:

```text
TaikaTranslator/
│
├── constitution.md                # SpecKit: Rules and boundaries
├── spec.md                        # SpecKit: Requirements & System specs
├── plan.md                        # SpecKit: Technical implementation plan
├── tasks.md                       # SpecKit: Atomic work tickets
│
├── pom.xml                        # Maven dependency configuration
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/taikatranslator/
│   │   │       ├── Main.java     # CLI Entrypoint
│   │   │       ├── core/         # Component Interfaces & Orchestration logic
│   │   │       ├── model/        # Unified Layout and Spatial Models
│   │   │       └── infra/        # Concrete Implementations (Azure, DeepL, POI, Python runner)
│   │   └── resources/
│   │       └── application.properties # API configuration defaults
│   └── test/
│       └── java/
│           └── com/taikatranslator/ # Unit & integration tests
│
└── vision/                        # Python Vision Helper
    ├── requirements.txt           # Python packages (torch, ultralytics, segment-anything, opencv)
    ├── segmenter.py               # YOLOv11 / SAM 3 model segmentation script
    └── models/                    # Directory containing downloaded weights
```

---

## 3. Libraries & Dependencies

### Java Orchestrator (Maven)
1. **PDF Processing:**
   - `org.apache.pdfbox:pdfbox:3.0.2` (converts input scanned PDF pages into 300 DPI PNGs).
2. **DOCX Generation:**
   - `org.docx4j:docx4j-JAXB-ReferenceImpl:11.4.11` OR `org.apache.poi:poi-ooxml:5.2.5`
   - *Recommendation:* **docx4j** offers robust control over advanced word elements like drawing shapes, absolute positioned text boxes, and complex XML manipulation required to achieve visual identity with bounding boxes.
3. **JSON Serialization:**
   - `com.fasterxml.jackson.core:jackson-databind:2.16.1` (to parse local communication JSONs and OCR models).
4. **HTTP Client & Utilities:**
   - Java 17 native `java.net.http.HttpClient` (lightweight, supports HTTP/2).
5. **Logging:**
   - `org.slf4j:slf4j-api:2.0.12` with `ch.qos.logback:logback-classic:1.5.3` for structured console/file logging.

### Python Vision Helper
- `opencv-python-headless`: Fast image manipulation.
- `ultralytics` (YOLOv11): Rapid object-detection for finding bounding boxes of stamps and signatures.
- `segment-anything` (SAM 3 / SAM): High-fidelity contoured masking and alpha-channel extraction of stamps/seals.
- `torch` & `torchvision`: Neural network execution.

---

## 4. Key Implementation Patterns

### A. Non-Hardcoded Coordinates & Relative Mapping
- We will parse physical page dimensions `(pageWidth, pageHeight)` from the Cloud API.
- All layout models will use **normalized coordinates** (floats from 0.0 to 1.0) for `left`, `top`, `width`, `height`.
- The `WordAssembler` will map normalized dimensions to Word's EMU (English Metric Unit) or Twip metrics based on target page size (e.g., Letter/A4).

### B. Resilient Self-Healing API Calls (Retry Pattern)
- Every external HTTP call is wrapped in a generic retry utility:
```java
public class RetryExecutor {
    public static <T> T executeWithRetry(RetryableAction<T> action, int maxRetries, long initialDelayMs) throws Exception {
        int attempt = 0;
        long delay = initialDelayMs;
        while (true) {
            try {
                return action.run();
            } catch (Exception e) {
                attempt++;
                if (attempt >= maxRetries) {
                    throw e;
                }
                // Exponential backoff with jitter
                long jitter = (long) (Math.random() * (delay * 0.1));
                Thread.sleep(delay + jitter);
                delay *= 2.0;
            }
        }
    }
}
```

### C. Text-Swell Compensation (Spanish)
- Compute estimated text bounding boxes in Spanish by comparing character/word count expansion ratios (~1.15 to 1.20).
- If bounding box limits are reached, the system will programmatically scale down the font size:
  - Font sizes will scale between 8pt and 12pt based on text length: `newSize = Math.max(minSize, originalSize * (originalLength / translatedLength))`.
- For tables, we will use percentage-based columns, allowing natural row height expansion while preserving table alignment.

---

## 5. Proposed Verification Plan

### Automated Tests
1. **Unit Tests:**
   - `LayoutParsingTest`: Verify that coordinates are successfully parsed and normalized from raw Azure/GCP OCR JSONs.
   - `RetryExecutorTest`: Test that exceptions trigger retries with accurate exponential delays.
   - `TextSwellTest`: Validate font-scaling calculations on English vs Spanish translation mockups.
2. **Integration Tests:**
   - `VisionProcessorIT`: Test the invocation of the Python segmentation helper using a mock image, verifying output PNG files.
   - `WordAssemblerIT`: Build and save a sample multi-page `.docx` containing overlapping images and structured tables, verifying file integrity with OpenXML SDK tools.

### Manual Verification
- Render the generated English and Spanish `.docx` files and compare them side-by-side with the scanned source PDF.
- Verify that stamps are correctly extracted (transparent backgrounds, no white border boxes) and located in the correct positions relative to neighboring text.

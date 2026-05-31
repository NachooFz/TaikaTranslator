# SpecKit Tickets: Document Extraction & Translation Pipeline (TaikaTranslator)

This task sheet defines the atomic, executable tickets required to build, integrate, and verify the pipeline.

---

## Phase 1: Environment & Tooling Setup

- [x] **TSK-1.1: Install Core System Dependencies**
  - Install Python 3.12+ using `winget` (Completed).
  - Install Apache Maven (Completed).
  - Verify installations via terminal checks (Completed).

- [x] **TSK-1.2: Install SpecKit and Graphify Tooling**
  - Install the SpecKit CLI (Completed - `speckit-status` installed).
  - Install Graphify (`graphifyy`) tool using python/pip (Completed).
  - Run the initial `/graphify .` command to map the workspace and generate the baseline `GRAPH_REPORT.md` (Completed).

---

## Phase 2: Project Scaffolding & Core Architecture

- [x] **TSK-2.1: Bootstrap Maven Java Project**
  - Create the `pom.xml` in the root directory (Completed).
  - Include dependencies: `pdfbox`, `poi-ooxml`, `poi-ooxml-full`, `jackson-databind`, `slf4j-api`, and `logback-classic` (Completed).
  - Verify build compile via `mvn clean compile` (Completed).

- [x] **TSK-2.2: Define Core Interfaces & Domain Models**
  - Create packages `com.taikatranslator.core` and `com.taikatranslator.model` (Completed).
  - Implement `VisionProcessor`, `DocumentExtractor`, `TextTranslator`, and `WordAssembler` interfaces (Completed).
  - Define unified `DocumentLayout` model classes containing `Paragraph`, `Table`, `Cell`, `TextBlock`, and coordinate bounds (Completed).

- [x] **TSK-2.3: Implement Resilient HTTP Client Utility**
  - Implement the `RetryExecutor` class with configurable max retries, exponential backoff, and jitter (Completed).
  - Set up a robust, decoupled wrapper for making raw HTTP POST/GET requests using Java's native `HttpClient` (Completed).

---

## Phase 3: Vision Helper Development (Python)

- [x] **TSK-3.1: Scaffolding Python Vision Module**
  - Create the `/vision` subdirectory (Completed).
  - Define `requirements.txt` containing `torch`, `torchvision`, `opencv-python-headless`, `ultralytics`, `segment-anything` (Completed).
  - Create `segmenter.py` CLI interface structure with argument parsers (Completed).

- [x] **TSK-3.2: Implement Artifact Detection & Masking**
  - Write YOLOv11/SAM 3 layout with highly robust local OpenCV contours fallback in `segmenter.py` (Completed).
  - Perform Contour Detection to extract artifacts as transparent PNG cutouts (Completed).
  - Write background-masking logic to erase these artifacts from the page, outputting a cleaned, OCR-friendly image (Completed).

- [x] **TSK-3.3: Java ProcessBuilder Runner Integration**
  - Implement the `ProcessBuilderVisionProcessor` in Java (Completed).
  - Write logic to safely execute Python scripts via `ProcessBuilder`, stream CLI logs, read error exit codes, and locate the resulting artifact PNGs (Completed).

---

## Phase 4: Integrations & Processing Logic (Java)

- [x] **TSK-4.1: Implement Structured Cloud Extraction Client**
  - Write `AzureDocumentExtractor` conforming to the `DocumentExtractor` interface (Completed).
  - Authenticate using local env parameters (endpoint, keys) (Completed).
  - Read full OCR JSON structures, parse table grids, capture font styling, and normalize coordinates (Completed).

- [x] **TSK-4.2: Implement Block-Level Translation Client**
  - Write `DeepLTranslator` conforming to `TextTranslator` (Completed).
  - Implement bulk text-translation requests to optimize round-trips (Completed).
  - Handle formatting escape tags to prevent machine-translation from altering layouts (Completed).

- [x] **TSK-4.3: Implement OpenXML Word Assembler**
  - Write `DocxWordAssembler` using Apache POI (Completed).
  - Reconstruct structured document text using indents and alignments matching source positions (Completed).
  - Draw/overlay transparent signature and stamp PNGs at exact mapped coordinate layers (Completed).

- [x] **TSK-4.4: Integrate Spanish Text-Swell Scaling**
  - Implement auto-scaling font size logic inside the assembler when translated Spanish string lengths swell beyond text boundaries (Completed).
  - Write table-cell width autosizing based on character count ratios (Completed).

---

## Phase 5: Pipeline Integration & Verification

- [x] **TSK-5.1: Build E2E Orchestrator CLI**
  - Connect all interfaces inside `Main.java` (Completed).
  - Parse CLI parameters: `--input`, `--output-dir` (Completed).
  - Setup structured logs tracking duration and success/failure bounds for each workflow step (Completed).

- [ ] **TSK-5.2: Write Unit & Integration Tests**
  - Write unit tests for layout normalization, text swell computation, and retry utility.
  - Write E2E integration test matching a sample scanned PDF to generated DOCX artifacts.

- [x] **TSK-5.3: Verification and Documentation**
  - Verify layout visual alignment using OpenXML rendering (Completed).
  - Create the `walkthrough.md` walkthrough showing system results, performance logs, and segmented image outputs (Completed).

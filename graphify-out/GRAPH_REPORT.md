# Graph Report - TaikaTranslator  (2026-05-31)

## Corpus Check
- 44 files · ~128,722 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 427 nodes · 675 edges · 33 communities (30 shown, 3 thin omitted)
- Extraction: 86% EXTRACTED · 14% INFERRED · 0% AMBIGUOUS · INFERRED: 96 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `60b9fcf0`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 26|Community 26]]

## God Nodes (most connected - your core abstractions)
1. `TableCell` - 15 edges
2. `TextBlock` - 14 edges
3. `DocumentLayout` - 13 edges
4. `TextStyle` - 13 edges
5. `AzureDocumentExtractor` - 11 edges
6. `BoundingBox` - 11 edges
7. `Main` - 10 edges
8. `String` - 10 edges
9. `analyzeResult` - 9 edges
10. `analyzeResult` - 9 edges

## Surprising Connections (you probably didn't know these)
- `MockDocumentExtractor` --implements--> `DocumentExtractor`  [EXTRACTED]
  src/main/java/com/taikatranslator/Main.java → src/main/java/com/taikatranslator/core/extractor/DocumentExtractor.java
- `MockTextTranslator` --implements--> `TextTranslator`  [EXTRACTED]
  src/main/java/com/taikatranslator/Main.java → src/main/java/com/taikatranslator/core/translator/TextTranslator.java
- `DocxWordAssembler` --implements--> `WordAssembler`  [EXTRACTED]
  src/main/java/com/taikatranslator/infra/assembler/DocxWordAssembler.java → src/main/java/com/taikatranslator/core/assembler/WordAssembler.java
- `AzureDocumentExtractor` --implements--> `DocumentExtractor`  [EXTRACTED]
  src/main/java/com/taikatranslator/infra/extractor/AzureDocumentExtractor.java → src/main/java/com/taikatranslator/core/extractor/DocumentExtractor.java
- `DeepLTranslator` --implements--> `TextTranslator`  [EXTRACTED]
  src/main/java/com/taikatranslator/infra/translator/DeepLTranslator.java → src/main/java/com/taikatranslator/core/translator/TextTranslator.java

## Import Cycles
- None detected.

## Communities (33 total, 3 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.11
Nodes (18): 1. System Architecture & Interfaces, 1. `VisionProcessor`, 2. Directory Structure & Layout, 2. `DocumentExtractor`, 3. Libraries & Dependencies, 3. `TextTranslator`, 4. Key Implementation Patterns, 4. `WordAssembler` (+10 more)

### Community 1 - "Community 1"
Cohesion: 0.12
Nodes (15): 1. Input Specifications, 2. Output Specifications, 3. Core Functional Requirements, 4. Non-Functional & Operational Requirements, F1. PDF Processing & Page Extraction, F2. Biomechanical & Visual Element Segmentation (Vision Helper), F3. Structured Layout & Font Analysis (Cloud OCR), F4. Block-Level Semantic Translation (+7 more)

### Community 2 - "Community 2"
Cohesion: 0.13
Nodes (14): 1. Core Principles, 2. Technology Stack & Component Boundaries, 3. Strict Architectural Rules (.cursorrules baseline), A. Core Orchestrator (Java), A. Dynamic Layouts (No Hardcoding), B. Idempotency & Scalability, B. Vision Helper (Python), C. Resilient API Integrations (Retry Pattern) (+6 more)

### Community 3 - "Community 3"
Cohesion: 0.08
Nodes (17): DocxWordAssembler, TextLine, BoundingBox, TextStyle, DocumentLayout, File, List, Override (+9 more)

### Community 4 - "Community 4"
Cohesion: 0.13
Nodes (16): BufferedImage, AzureDocumentExtractor, DocumentExtractor, HttpClientWrapper, JsonNode, Map, DocumentLayout, File (+8 more)

### Community 5 - "Community 5"
Cohesion: 0.29
Nodes (6): Phase 1: Environment & Tooling Setup, Phase 2: Project Scaffolding & Core Architecture, Phase 3: Vision Helper Development (Python), Phase 4: Integrations & Processing Logic (Java), Phase 5: Pipeline Integration & Verification, SpecKit Tickets: Document Extraction & Translation Pipeline (TaikaTranslator)

### Community 6 - "Community 6"
Cohesion: 0.15
Nodes (14): InlineRun, DocumentLayout, File, InlineRun, List, Map, Override, String (+6 more)

### Community 7 - "Community 7"
Cohesion: 0.10
Nodes (16): ArtifactInfo, File, VisionResult, BoundingBox, File, List, Override, String (+8 more)

### Community 10 - "Community 10"
Cohesion: 0.10
Nodes (13): AssemblyException, Exception, ExtractionException, String, Throwable, String, Throwable, String (+5 more)

### Community 11 - "Community 11"
Cohesion: 0.13
Nodes (4): TableCell, BoundingBox, Override, String

### Community 12 - "Community 12"
Cohesion: 0.20
Nodes (5): TextBlock, BoundingBox, Override, String, TextStyle

### Community 13 - "Community 13"
Cohesion: 0.23
Nodes (6): Table, BoundingBox, List, Override, String, TableRow

### Community 15 - "Community 15"
Cohesion: 0.29
Nodes (5): TableRow, List, Override, String, TableCell

### Community 16 - "Community 16"
Cohesion: 0.24
Nodes (8): RenderableElement, WordAssembler, Object, DocumentLayout, File, List, VisionResult, Type

### Community 17 - "Community 17"
Cohesion: 0.23
Nodes (12): extract_contours_fallback(), extract_sam_segmentation(), main(), parse_args(), Refined local fallback using OpenCV contour analysis.     Identifies high-varian, Refined local fallback using OpenCV contour analysis.     Identifies high-varian, High-fidelity segmentation using Segment Anything Model (SAM) from Ultralytics, Refined local fallback using OpenCV contour analysis.     Identifies high-varian (+4 more)

### Community 18 - "Community 18"
Cohesion: 0.21
Nodes (3): InlineRun, Override, String

### Community 19 - "Community 19"
Cohesion: 0.15
Nodes (12): 1. System Architecture, 2. Core Operational Flow, 3. Installation & Setup, 4. How to Execute the Application, 5. Directory Structure, 6. Development & Knowledge Graph Management, Option A: Standard Build & Execute (Loads from `.env`), Option B: Override Configuration via CLI Arguments (+4 more)

### Community 20 - "Community 20"
Cohesion: 0.20
Nodes (9): 1. System Architecture & Codebase Map, 2. Core Implementation Highlights, 3. Compilation & Structural Verification, 4. How to Execute E2E Pipeline, 5. Verification Run Results (May 30, 2026), A. Python Vision Helper (`vision/segmenter.py`), B. Decoupled Java Interfaces, C. Resiliency & Text Swell (Spanish) (+1 more)

### Community 21 - "Community 21"
Cohesion: 0.16
Nodes (10): RetryableAction, RetryExecutor, List, String, List, Override, String, T (+2 more)

### Community 22 - "Community 22"
Cohesion: 0.15
Nodes (12): analyzeResult, apiVersion, content, modelId, pages, paragraphs, stringIndexType, styles (+4 more)

### Community 23 - "Community 23"
Cohesion: 0.15
Nodes (12): analyzeResult, apiVersion, content, modelId, pages, paragraphs, stringIndexType, styles (+4 more)

### Community 26 - "Community 26"
Cohesion: 0.15
Nodes (7): DocumentLayout, List, Override, String, TextBlock, Table, TextBlock

## Knowledge Gaps
- **113 isolated node(s):** `status`, `createdDateTime`, `lastUpdatedDateTime`, `apiVersion`, `modelId` (+108 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **3 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TextBlock` connect `Community 12` to `Community 6`?**
  _High betweenness centrality (0.030) - this node is a cross-community bridge._
- **Why does `WordAssembler` connect `Community 16` to `Community 3`, `Community 6`?**
  _High betweenness centrality (0.021) - this node is a cross-community bridge._
- **What connects `status`, `createdDateTime`, `lastUpdatedDateTime` to the rest of the system?**
  _119 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.10526315789473684 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.125 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.13333333333333333 - nodes in this community are weakly interconnected._
- **Should `Community 3` be split into smaller, more focused modules?**
  _Cohesion score 0.08470588235294117 - nodes in this community are weakly interconnected._
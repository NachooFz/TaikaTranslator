# Graph Report - TaikaTranslator  (2026-06-17)

## Corpus Check
- 67 files · ~43,607 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 710 nodes · 1005 edges · 55 communities (50 shown, 5 thin omitted)
- Extraction: 89% EXTRACTED · 11% INFERRED · 0% AMBIGUOUS · INFERRED: 115 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `b8679490`
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
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]
- [[_COMMUNITY_Community 48|Community 48]]
- [[_COMMUNITY_Community 50|Community 50]]

## God Nodes (most connected - your core abstractions)
1. `TableCell` - 15 edges
2. `TextBlock` - 14 edges
3. `DocumentLayout` - 13 edges
4. `TextStyle` - 13 edges
5. `Tasks: [FEATURE NAME]` - 13 edges
6. `files` - 11 edges
7. `BoundingBox` - 11 edges
8. `files` - 10 edges
9. `Main` - 10 edges
10. `String` - 10 edges

## Surprising Connections (you probably didn't know these)
- `MockDocumentExtractor` --implements--> `DocumentExtractor`  [EXTRACTED]
  src/main/java/com/udocmachine/Main.java → src/main/java/com/udocmachine/core/extractor/DocumentExtractor.java
- `MockTextTranslator` --implements--> `TextTranslator`  [EXTRACTED]
  src/main/java/com/udocmachine/Main.java → src/main/java/com/udocmachine/core/translator/TextTranslator.java
- `DocxWordAssembler` --implements--> `WordAssembler`  [EXTRACTED]
  src/main/java/com/udocmachine/infra/assembler/DocxWordAssembler.java → src/main/java/com/udocmachine/core/assembler/WordAssembler.java
- `MockDocumentExtractor` --implements--> `DocumentExtractor`  [EXTRACTED]
  src/main/java/com/udocmachine/core/pipeline/TranslationPipeline.java → src/main/java/com/udocmachine/core/extractor/DocumentExtractor.java
- `DeepLTranslator` --implements--> `TextTranslator`  [EXTRACTED]
  src/main/java/com/udocmachine/infra/translator/DeepLTranslator.java → src/main/java/com/udocmachine/core/translator/TextTranslator.java

## Import Cycles
- None detected.

## Communities (55 total, 5 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.11
Nodes (18): 1. System Architecture & Interfaces, 1. `VisionProcessor`, 2. Directory Structure & Layout, 2. `DocumentExtractor`, 3. Libraries & Dependencies, 3. `TextTranslator`, 4. Key Implementation Patterns, 4. `WordAssembler` (+10 more)

### Community 1 - "Community 1"
Cohesion: 0.12
Nodes (15): 1. Input Specifications, 2. Output Specifications, 3. Core Functional Requirements, 4. Non-Functional & Operational Requirements, F1. PDF Processing & Page Extraction, F2. Biomechanical & Visual Element Segmentation (Vision Helper), F3. Structured Layout & Font Analysis (Cloud OCR), F4. Block-Level Semantic Translation (+7 more)

### Community 2 - "Community 2"
Cohesion: 0.15
Nodes (15): 1. Core Principles, 2. Technology Stack & Component Boundaries, 3. Strict Architectural Rules (.cursorrules baseline), A. Core Orchestrator (Java), A. Dynamic Layouts (No Hardcoding), B. Idempotency & Scalability, B. Vision Helper (Python), C. Resilient API Integrations (Retry Pattern) (+7 more)

### Community 3 - "Community 3"
Cohesion: 0.06
Nodes (23): DocxWordAssembler, TextLine, BoundingBox, DocumentLayout, TextStyle, DocumentLayout, File, List (+15 more)

### Community 4 - "Community 4"
Cohesion: 0.09
Nodes (21): AzureDocumentExtractor, DocumentExtractor, HttpClientWrapper, JsonNode, RetryableAction, RetryExecutor, DocumentLayout, File (+13 more)

### Community 5 - "Community 5"
Cohesion: 0.29
Nodes (6): Phase 1: Environment & Tooling Setup, Phase 2: Project Scaffolding & Core Architecture, Phase 3: Vision Helper Development (Python), Phase 4: Integrations & Processing Logic (Java), Phase 5: Pipeline Integration & Verification, SpecKit Tickets: Document Extraction & Translation Pipeline (TaikaTranslator)

### Community 6 - "Community 6"
Cohesion: 0.07
Nodes (26): Dependencies & Execution Order, Format: `[ID] [P?] [Story] Description`, Implementation for User Story 1, Implementation for User Story 2, Implementation for User Story 3, Implementation Strategy, Incremental Delivery, MVP First (User Story 1 Only) (+18 more)

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
Cohesion: 0.13
Nodes (14): MockDocumentExtractor, MockTextTranslator, PipelineResult, TranslationPipeline, DocumentLayout, File, InlineRun, List (+6 more)

### Community 13 - "Community 13"
Cohesion: 0.23
Nodes (6): Table, BoundingBox, List, Override, String, TableRow

### Community 14 - "Community 14"
Cohesion: 0.08
Nodes (25): 1. Initialize Analysis Context, 2. Load Artifacts (Progressive Disclosure), 3. Build Semantic Models, 4. Detection Passes (Token-Efficient Analysis), 5. Severity Assignment, 6. Produce Compact Analysis Report, 7. Provide Next Actions, 8. Offer Remediation (+17 more)

### Community 15 - "Community 15"
Cohesion: 0.29
Nodes (5): TableRow, List, Override, String, TableCell

### Community 16 - "Community 16"
Cohesion: 0.20
Nodes (11): DocumentLayout, File, InlineRun, List, Map, Override, String, TextBlock (+3 more)

### Community 17 - "Community 17"
Cohesion: 0.21
Nodes (13): extract_contours_fallback(), extract_sam_segmentation(), main(), parse_args(), Refined local fallback using OpenCV contour analysis.     Identifies high-varian, Refined local fallback using OpenCV contour analysis.     Identifies high-varian, High-fidelity segmentation using Segment Anything Model (SAM) from Ultralytics, Refined local fallback using OpenCV contour analysis.     Identifies high-varian (+5 more)

### Community 18 - "Community 18"
Cohesion: 0.21
Nodes (3): InlineRun, Override, String

### Community 19 - "Community 19"
Cohesion: 0.08
Nodes (30): 1. System Architecture, 2. Core Operational Flow, 3. Installation & Setup, 3. Quick Start & Setup, 4. How to Execute the Application, 5. Directory Structure, 6. Development & Knowledge Graph Management, 6. HTTP API Documentation (+22 more)

### Community 20 - "Community 20"
Cohesion: 0.18
Nodes (10): 1. System Architecture & Codebase Map, 2. Core Implementation Highlights, 3. Compilation & Structural Verification, 4. How to Execute E2E Pipeline, 5. Verification Run Results & Form Formatting Overlay (June 2, 2026), 5. Verification Run Results (May 30, 2026), A. Python Vision Helper (`vision/segmenter.py`), B. Decoupled Java Interfaces (+2 more)

### Community 21 - "Community 21"
Cohesion: 0.17
Nodes (7): TextBlock, BoundingBox, InlineRun, List, Override, String, TextStyle

### Community 22 - "Community 22"
Cohesion: 0.13
Nodes (14): files, .specify/scripts/powershell/check-prerequisites.ps1, .specify/scripts/powershell/common.ps1, .specify/scripts/powershell/create-new-feature.ps1, .specify/scripts/powershell/setup-plan.ps1, .specify/scripts/powershell/setup-tasks.ps1, .specify/templates/checklist-template.md, .specify/templates/constitution-template.md (+6 more)

### Community 23 - "Community 23"
Cohesion: 0.28
Nodes (6): Context, TranslationServer, File, Map, Object, String

### Community 24 - "Community 24"
Cohesion: 0.14
Nodes (13): files, .agents/skills/speckit-analyze/SKILL.md, .agents/skills/speckit-checklist/SKILL.md, .agents/skills/speckit-clarify/SKILL.md, .agents/skills/speckit-constitution/SKILL.md, .agents/skills/speckit-implement/SKILL.md, .agents/skills/speckit-plan/SKILL.md, .agents/skills/speckit-specify/SKILL.md (+5 more)

### Community 25 - "Community 25"
Cohesion: 0.23
Nodes (9): Find-SpecifyRoot(), Format-SpecKitCommand(), Get-CurrentBranch(), Get-FeaturePathsEnv(), Get-InvokeSeparator(), Get-Python3Command(), Get-RepoRoot(), Resolve-TemplateContent() (+1 more)

### Community 26 - "Community 26"
Cohesion: 0.15
Nodes (12): Assumptions, Edge Cases, Feature Specification: [FEATURE NAME], Functional Requirements, Key Entities *(include if feature involves data)*, Measurable Outcomes, Requirements *(mandatory)*, Success Criteria *(mandatory)* (+4 more)

### Community 27 - "Community 27"
Cohesion: 0.50
Nodes (3): RenderableElement, Object, Type

### Community 28 - "Community 28"
Cohesion: 0.18
Nodes (10): Completion Report, Done When, Key rules, Mandatory Post-Execution Hooks, Outline, Phase 0: Outline & Research, Phase 1: Design & Contracts, Phases (+2 more)

### Community 29 - "Community 29"
Cohesion: 0.18
Nodes (10): Completion Report, Done When, For AI Generation, Mandatory Post-Execution Hooks, Outline, Pre-Execution Checks, Quick Guidelines, Section Requirements (+2 more)

### Community 30 - "Community 30"
Cohesion: 0.48
Nodes (5): WordAssembler, DocumentLayout, File, List, VisionResult

### Community 31 - "Community 31"
Cohesion: 0.18
Nodes (10): Checklist Format (REQUIRED), Completion Report, Done When, Mandatory Post-Execution Hooks, Outline, Phase Structure, Pre-Execution Checks, Task Generation Rules (+2 more)

### Community 32 - "Community 32"
Cohesion: 0.18
Nodes (10): Core Principles, Governance, [PRINCIPLE_1_NAME], [PRINCIPLE_2_NAME], [PRINCIPLE_3_NAME], [PRINCIPLE_4_NAME], [PRINCIPLE_5_NAME], [PROJECT_NAME] Constitution (+2 more)

### Community 33 - "Community 33"
Cohesion: 0.18
Nodes (10): Core Principles, Governance, [PRINCIPLE_1_NAME], [PRINCIPLE_2_NAME], [PRINCIPLE_3_NAME], [PRINCIPLE_4_NAME], [PRINCIPLE_5_NAME], [PROJECT_NAME] Constitution (+2 more)

### Community 34 - "Community 34"
Cohesion: 0.20
Nodes (9): invoke_separator, script, default_integration, installed_integrations, integration, integration_settings, agy, integration_state_schema (+1 more)

### Community 35 - "Community 35"
Cohesion: 0.20
Nodes (9): schema_version, description, installed_at, name, source, updated_at, version, workflows (+1 more)

### Community 36 - "Community 36"
Cohesion: 0.22
Nodes (8): Complexity Tracking, Constitution Check, Documentation (this feature), Implementation Plan: [FEATURE], Project Structure, Source Code (repository root), Summary, Technical Context

### Community 37 - "Community 37"
Cohesion: 0.25
Nodes (7): Anti-Examples: What NOT To Do, Checklist Purpose: "Unit Tests for English", Example Checklist Types & Sample Items, Execution Steps, Post-Execution Checks, Pre-Execution Checks, User Input

### Community 38 - "Community 38"
Cohesion: 0.25
Nodes (7): ai, ai_skills, feature_numbering, here, integration, script, speckit_version

### Community 39 - "Community 39"
Cohesion: 0.29
Nodes (6): Coding Agent Context Extension, Commands, Configuration, Disable, Requirements, Why an extension?

### Community 40 - "Community 40"
Cohesion: 0.29
Nodes (6): Completion Report, Done When, Mandatory Post-Execution Hooks, Outline, Pre-Execution Checks, User Input

### Community 41 - "Community 41"
Cohesion: 0.29
Nodes (6): Completion Report, Done When, Mandatory Post-Execution Hooks, Outline, Pre-Execution Checks, User Input

### Community 42 - "Community 42"
Cohesion: 0.40
Nodes (4): Outline, Post-Execution Checks, Pre-Execution Checks, User Input

### Community 43 - "Community 43"
Cohesion: 0.40
Nodes (4): Outline, Post-Execution Checks, Pre-Execution Checks, User Input

### Community 44 - "Community 44"
Cohesion: 0.40
Nodes (4): [Category 1], [Category 2], [CHECKLIST TYPE] Checklist: [FEATURE NAME], Notes

### Community 45 - "Community 45"
Cohesion: 0.50
Nodes (3): Behavior, Execution, Update Coding Agent Context

### Community 46 - "Community 46"
Cohesion: 0.50
Nodes (3): Behavior, Execution, Update Coding Agent Context

## Knowledge Gaps
- **285 isolated node(s):** `ai`, `ai_skills`, `feature_numbering`, `here`, `integration` (+280 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **5 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `TextBlock` connect `Community 21` to `Community 3`?**
  _High betweenness centrality (0.012) - this node is a cross-community bridge._
- **Why does `WordAssembler` connect `Community 30` to `Community 16`, `Community 3`, `Community 12`?**
  _High betweenness centrality (0.012) - this node is a cross-community bridge._
- **Why does `DocumentExtractor` connect `Community 4` to `Community 16`, `Community 12`?**
  _High betweenness centrality (0.008) - this node is a cross-community bridge._
- **What connects `ai`, `ai_skills`, `feature_numbering` to the rest of the system?**
  _292 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.10526315789473684 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.125 - nodes in this community are weakly interconnected._
- **Should `Community 3` be split into smaller, more focused modules?**
  _Cohesion score 0.05859969558599695 - nodes in this community are weakly interconnected._
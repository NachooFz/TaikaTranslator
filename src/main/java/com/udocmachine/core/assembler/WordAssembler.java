package com.udocmachine.core.assembler;

import java.io.File;
import java.util.List;
import com.udocmachine.model.DocumentLayout;
import com.udocmachine.core.vision.VisionResult;

public interface WordAssembler {
    /**
     * Generates a single unified .docx representation based on multiple page layouts, translated texts, and visual artifacts.
     * @param layouts The list of structural document page layouts.
     * @param visionResults The list of visual artifacts extracted from each page.
     * @param outputFile The file path where the unified .docx should be written.
     * @throws AssemblyException If OpenXML assembly failure occurs.
     */
    void assemble(List<DocumentLayout> layouts, List<VisionResult> visionResults, File outputFile) throws AssemblyException;
}

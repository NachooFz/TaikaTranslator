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
     * @throws AssemblyException If OpenXML assembly failure occurs.
     */
    void assemble(DocumentLayout layout, VisionResult visionResult, File outputFile) throws AssemblyException;
}

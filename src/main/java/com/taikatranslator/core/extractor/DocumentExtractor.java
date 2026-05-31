package com.taikatranslator.core.extractor;

import java.io.File;
import com.taikatranslator.model.DocumentLayout;

public interface DocumentExtractor {
    /**
     * Extracts structural layouts and metadata from the cleaned image.
     * @param cleanPageImage The masked page image with visual artifacts removed.
     * @return A parsed DocumentLayout model containing paragraph coordinates, fonts, tables, etc.
     * @throws ExtractionException If any cloud OCR error occurs.
     */
    DocumentLayout extractLayout(File cleanPageImage) throws ExtractionException;
}

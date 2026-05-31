package com.taikatranslator.core.translator;

import java.util.List;

public interface TextTranslator {
    /**
     * Translates a list of text blocks from source language (English) to target language (Spanish).
     * @param textBlocks The list of text blocks.
     * @return The translated list of text blocks in the same order.
     * @throws TranslationException If translation engine error occurs.
     */
    List<String> translate(List<String> textBlocks) throws TranslationException;
}

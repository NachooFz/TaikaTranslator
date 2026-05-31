package com.taikatranslator.core.vision;

import java.io.File;

public interface VisionProcessor {
    /**
     * Preprocesses page images to segment visual artifacts and generate masked, clean pages.
     * @param sourcePageImage The high-res page image.
     * @param outputDirectory The directory to save cropped artifacts.
     * @return VisionResult containing paths to cropped artifacts and the clean page image.
     * @throws VisionException If any model segmentation error occurs.
     */
    VisionResult processPage(File sourcePageImage, File outputDirectory) throws VisionException;
}

package com.taikatranslator.infra.assembler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Comparator;
import java.util.List;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.taikatranslator.core.assembler.AssemblyException;
import com.taikatranslator.core.assembler.WordAssembler;
import com.taikatranslator.core.vision.VisionResult;
import com.taikatranslator.model.BoundingBox;
import com.taikatranslator.model.DocumentLayout;
import com.taikatranslator.model.Table;
import com.taikatranslator.model.TableCell;
import com.taikatranslator.model.TableRow;
import com.taikatranslator.model.TextBlock;
import com.taikatranslator.model.TextStyle;

public class DocxWordAssembler implements WordAssembler {
    private static final Logger log = LoggerFactory.getLogger(DocxWordAssembler.class);

    // standard Word page dimensions (Letter: 8.5 x 11 inches in Twips/dxa: 1 inch = 1440 twips)
    private static final int PAGE_WIDTH_TWIPS = 12240; // 8.5 * 1440
    private static final int PAGE_HEIGHT_TWIPS = 15840; // 11 * 1440

    @Override
    public void assemble(DocumentLayout layout, VisionResult visionResult, File outputFile) throws AssemblyException {
        log.info("Assembling .docx file using high-level POI API: {}", outputFile.getAbsolutePath());
        
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(outputFile)) {
            
            // 1. Sort text blocks by reading order
            List<TextBlock> textBlocks = layout.getTextBlocks();
            textBlocks.sort(Comparator.comparingInt(TextBlock::getReadingOrder));
            
            // 2. Reconstruct Text Paragraphs
            for (TextBlock block : textBlocks) {
                XWPFParagraph paragraph = doc.createParagraph();
                
                // Adjust alignment based on bounding box positioning
                double leftRatio = block.getBoundingBox().getLeft();
                if (leftRatio > 0.6) {
                    paragraph.setAlignment(ParagraphAlignment.RIGHT);
                } else if (leftRatio > 0.3 && leftRatio < 0.6) {
                    paragraph.setAlignment(ParagraphAlignment.CENTER);
                } else {
                    paragraph.setAlignment(ParagraphAlignment.LEFT);
                }
                
                // Set relative margins to match the bounding box left offset
                int leftIndentTwips = (int) (leftRatio * (PAGE_WIDTH_TWIPS - 2880)); // Subtract standard margins
                paragraph.setIndentationLeft(Math.max(0, leftIndentTwips));
                
                // Enforce spacing after
                paragraph.setSpacingAfter(120); // 6pt
                
                XWPFRun run = paragraph.createRun();
                run.setText(block.getText());
                
                // Apply visual styling
                TextStyle style = block.getTextStyle();
                if (style != null) {
                    run.setFontFamily(style.getFontName());
                    run.setBold(style.isBold());
                    run.setItalic(style.isItalic());
                    
                    // Dynamic Spanish Text-Swell Font Scaling
                    double scaledFontSize = style.getFontSize();
                    if (block.getText().length() > 100) {
                        // Shrink font size slightly if text block is long to prevent swell breakage
                        scaledFontSize = Math.max(9.0, style.getFontSize() * 0.9);
                    }
                    run.setFontSize((int) Math.round(scaledFontSize));
                    
                    // Format and apply hex colors
                    String hex = style.getHexColor().replace("#", "");
                    if (hex.length() == 6) {
                        run.setColor(hex);
                    }
                }
            }
            
            // 3. Reconstruct Tables
            for (Table tableModel : layout.getTables()) {
                List<TableRow> rows = tableModel.getRows();
                if (rows.isEmpty()) continue;
                
                int maxCols = 0;
                for (TableRow r : rows) {
                    maxCols = Math.max(maxCols, r.getCells().size());
                }
                
                XWPFTable table = doc.createTable(rows.size(), maxCols);
                
                // Enforce width relative to page dimensions
                double normWidth = tableModel.getBoundingBox().getWidth();
                int tableWidthTwips = (int) (normWidth * (PAGE_WIDTH_TWIPS - 2880));
                
                // Set table width (high-level POI API)
                table.setWidth(tableWidthTwips);

                // Populate cells
                for (int rIdx = 0; rIdx < rows.size(); rIdx++) {
                    XWPFTableRow tableRow = table.getRow(rIdx);
                    TableRow modelRow = rows.get(rIdx);
                    List<TableCell> modelCells = modelRow.getCells();
                    
                    for (int cIdx = 0; cIdx < modelCells.size(); cIdx++) {
                        XWPFTableCell tableCell = tableRow.getCell(cIdx);
                        if (tableCell == null) {
                            tableCell = tableRow.createCell();
                        }
                        
                        TableCell modelCell = modelCells.get(cIdx);
                        tableCell.setText(modelCell.getText());
                        
                        // Width configuration for individual cells (based on relative bounding box width)
                        double cellWidthRatio = modelCell.getBoundingBox().getWidth();
                        int cellWidthTwips = (int) (cellWidthRatio * (PAGE_WIDTH_TWIPS - 2880));
                        
                        // Set cell width (high-level POI API)
                        tableCell.setWidth(String.valueOf(cellWidthTwips));
                    }
                }
            }
            
            // 4. Draw & Overlay Segmented Visual Elements (Stamps, seals, signatures)
            if (visionResult != null && visionResult.getArtifacts() != null) {
                for (VisionResult.ArtifactInfo artifact : visionResult.getArtifacts()) {
                    File file = artifact.getArtifactFile();
                    if (!file.exists()) continue;
                    
                    XWPFParagraph imgPara = doc.createParagraph();
                    imgPara.setAlignment(ParagraphAlignment.LEFT);
                    
                    // Compute absolute horizontal offset to place the image
                    BoundingBox box = artifact.getBoundingBox();
                    int leftIndentTwips = (int) (box.getLeft() * (PAGE_WIDTH_TWIPS - 2880));
                    imgPara.setIndentationLeft(Math.max(0, leftIndentTwips));
                    
                    XWPFRun imgRun = imgPara.createRun();
                    
                    // Map normalized coordinates back to target pixels/inches (96 DPI)
                    int targetWidthPx = (int) (box.getWidth() * 8.5 * 96);
                    int targetHeightPx = (int) (box.getHeight() * 11 * 96);
                    
                    // Ensure valid dimensions
                    int w = Math.max(10, Math.min(600, targetWidthPx));
                    int h = Math.max(10, Math.min(800, targetHeightPx));
                    
                    log.info("Embedding transparent visual artifact {} at dimensions {}x{}", 
                            file.getName(), w, h);
                    
                    try (FileInputStream fis = new FileInputStream(file)) {
                        imgRun.addPicture(fis, 
                                XWPFDocument.PICTURE_TYPE_PNG, 
                                file.getName(), 
                                Units.toEMU(w), 
                                Units.toEMU(h));
                    }
                }
            }
            
            doc.write(out);
            log.info("Document successfully assembled.");
            
        } catch (Exception e) {
            log.error("Failed to assemble DOCX document", e);
            throw new AssemblyException("Failed to assemble word document: " + e.getMessage(), e);
        }
    }
}

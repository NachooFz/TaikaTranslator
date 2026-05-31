package com.taikatranslator.infra.assembler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
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

// OpenXML schema imports from poi-ooxml-full
import java.math.BigInteger;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTDrawing;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTInline;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTAnchor;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTPosH;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTPosV;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.STRelFromH;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.STRelFromV;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveSize2D;
import org.openxmlformats.schemas.drawingml.x2006.main.CTGraphicalObject;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPoint2D;

public class DocxWordAssembler implements WordAssembler {
    private static final Logger log = LoggerFactory.getLogger(DocxWordAssembler.class);

    // standard Word page dimensions (Letter: 8.5 x 11 inches in Twips/dxa: 1 inch = 1440 twips)
    private static final int PAGE_WIDTH_TWIPS = 12240; // 8.5 * 1440
    private static final int PAGE_HEIGHT_TWIPS = 15840; // 11 * 1440

    private static class RenderableElement {
        enum Type { TEXT, TABLE, ARTIFACT }
        final Type type;
        final double top;
        final Object element;

        RenderableElement(Type type, double top, Object element) {
            this.type = type;
            this.top = top;
            this.element = element;
        }
    }

    @Override
    public void assemble(List<DocumentLayout> layouts, List<VisionResult> visionResults, File outputFile) throws AssemblyException {
        log.info("Assembling multi-page .docx file: {}", outputFile.getAbsolutePath());
        
        try (XWPFDocument doc = new XWPFDocument();
             FileOutputStream out = new FileOutputStream(outputFile)) {
             
            // Configure absolute zero-margin layout to align perfectly with absolute floating image coordinates
            CTSectPr sectPr = doc.getDocument().getBody().getSectPr();
            if (sectPr == null) {
                sectPr = doc.getDocument().getBody().addNewSectPr();
            }
            CTPageMar pageMar = sectPr.getPgMar();
            if (pageMar == null) {
                pageMar = sectPr.addNewPgMar();
            }
            pageMar.setTop(BigInteger.valueOf(0));
            pageMar.setBottom(BigInteger.valueOf(0));
            pageMar.setLeft(BigInteger.valueOf(0));
            pageMar.setRight(BigInteger.valueOf(0));
             
            for (int p = 0; p < layouts.size(); p++) {
                DocumentLayout layout = layouts.get(p);
                VisionResult visionResult = (p < visionResults.size()) ? visionResults.get(p) : null;
                
                log.info("Processing page {}/{} inside unified document...", p + 1, layouts.size());
                assemblePage(doc, layout, visionResult);
                
                // If not the last page, insert a hard page break
                if (p < layouts.size() - 1) {
                    XWPFParagraph breakPara = doc.createParagraph();
                    XWPFRun breakRun = breakPara.createRun();
                    breakRun.addBreak(org.apache.poi.xwpf.usermodel.BreakType.PAGE);
                }
            }
            
            doc.write(out);
            log.info("Unified document successfully written to: {}", outputFile.getAbsolutePath());
            
        } catch (Exception e) {
            log.error("Failed to assemble unified DOCX document", e);
            throw new AssemblyException("Failed to assemble word document: " + e.getMessage(), e);
        }
    }

    private void assemblePage(XWPFDocument doc, DocumentLayout layout, VisionResult visionResult) throws Exception {
        // 1. Collect all layout components on the page
        List<RenderableElement> elements = new ArrayList<>();
        
        // Add TextBlocks
        if (layout.getTextBlocks() != null) {
            for (TextBlock block : layout.getTextBlocks()) {
                double top = block.getBoundingBox() != null ? block.getBoundingBox().getTop() : 0.0;
                elements.add(new RenderableElement(RenderableElement.Type.TEXT, top, block));
            }
        }
        
        // Add Tables
        if (layout.getTables() != null) {
            for (Table table : layout.getTables()) {
                double top = table.getBoundingBox() != null ? table.getBoundingBox().getTop() : 0.0;
                elements.add(new RenderableElement(RenderableElement.Type.TABLE, top, table));
            }
        }
        
        // Add Visual Artifacts (Signatures, stamps, seals)
        if (visionResult != null && visionResult.getArtifacts() != null) {
            for (VisionResult.ArtifactInfo artifact : visionResult.getArtifacts()) {
                double top = artifact.getBoundingBox() != null ? artifact.getBoundingBox().getTop() : 0.0;
                elements.add(new RenderableElement(RenderableElement.Type.ARTIFACT, top, artifact));
            }
        }
        
        // 2. Sort all elements dynamically by their vertical position (top coordinate)
        elements.sort(Comparator.comparingDouble(e -> e.top));
        
        // Track the current active paragraph to anchor floating images to
        XWPFParagraph anchorPara = null;
        
        // Track absolute vertical cursor in Twips relative to physical page top
        int currentTopTwips = 0;
        
        // 3. Sequentially reconstruct elements in their natural vertical order
        for (RenderableElement element : elements) {
            if (element.type == RenderableElement.Type.TEXT) {
                TextBlock block = (TextBlock) element.element;
                XWPFParagraph paragraph = doc.createParagraph();
                anchorPara = paragraph; // update active anchor paragraph
                
                // Adjust alignment based on bounding box positioning
                double leftRatio = block.getBoundingBox() != null ? block.getBoundingBox().getLeft() : 0.0;
                if (leftRatio > 0.6) {
                    paragraph.setAlignment(ParagraphAlignment.RIGHT);
                } else if (leftRatio > 0.3 && leftRatio < 0.6) {
                    paragraph.setAlignment(ParagraphAlignment.CENTER);
                } else {
                    paragraph.setAlignment(ParagraphAlignment.LEFT);
                }
                
                // Set relative margins to match the bounding box left offset (0 margins, so full width)
                int leftIndentTwips = (int) (leftRatio * PAGE_WIDTH_TWIPS);
                paragraph.setIndentationLeft(Math.max(0, leftIndentTwips));
                
                // Set absolute vertical positioning using spacing before
                double targetTop = block.getBoundingBox() != null ? block.getBoundingBox().getTop() : 0.0;
                int targetTopTwips = (int) (targetTop * PAGE_HEIGHT_TWIPS);
                int spacingBefore = targetTopTwips - currentTopTwips;
                paragraph.setSpacingBefore(Math.max(0, spacingBefore));
                paragraph.setSpacingAfter(0); // eliminate default swelling
                
                // Update cursor to bottom of current block
                double blockHeight = block.getBoundingBox() != null ? block.getBoundingBox().getHeight() : 0.02;
                currentTopTwips = targetTopTwips + (int) (blockHeight * PAGE_HEIGHT_TWIPS);
                
                // Render styled inline runs or fallback to standard text mapping with newline split support
                List<com.taikatranslator.model.InlineRun> inlineRuns = block.getInlineRuns();
                if (inlineRuns == null || inlineRuns.isEmpty()) {
                    XWPFRun run = paragraph.createRun();
                    String text = block.getText();
                    if (text != null) {
                        String[] lines = text.split("\\r?\\n", -1);
                        for (int i = 0; i < lines.length; i++) {
                            run.setText(lines[i]);
                            if (i < lines.length - 1) {
                                run.addBreak();
                            }
                        }
                    }
                    applyRunStyle(run, block.getTextStyle(), block.getText());
                } else {
                    for (com.taikatranslator.model.InlineRun inlineRun : inlineRuns) {
                        XWPFRun run = paragraph.createRun();
                        String text = inlineRun.getText();
                        if (text != null) {
                            String[] lines = text.split("\\r?\\n", -1);
                            for (int i = 0; i < lines.length; i++) {
                                run.setText(lines[i]);
                                if (i < lines.length - 1) {
                                    run.addBreak();
                                }
                            }
                        }
                        
                        applyRunStyle(run, block.getTextStyle(), block.getText());
                        if (inlineRun.isBold()) {
                            run.setBold(true);
                        }
                        if (inlineRun.isItalic()) {
                            run.setItalic(true);
                        }
                    }
                }
                
            } else if (element.type == RenderableElement.Type.TABLE) {
                Table tableModel = (Table) element.element;
                
                // Absolute vertical positioning using an empty spacing paragraph
                double targetTop = tableModel.getBoundingBox() != null ? tableModel.getBoundingBox().getTop() : 0.0;
                int targetTopTwips = (int) (targetTop * PAGE_HEIGHT_TWIPS);
                int spacingBefore = targetTopTwips - currentTopTwips;
                if (spacingBefore > 0) {
                    XWPFParagraph spacingPara = doc.createParagraph();
                    spacingPara.setSpacingBefore(spacingBefore);
                    spacingPara.setSpacingAfter(0);
                    // Make it tiny
                    XWPFRun r = spacingPara.createRun();
                    r.setFontSize(1);
                }
                
                List<TableRow> rows = tableModel.getRows();
                if (rows.isEmpty()) continue;
                
                int maxCols = 0;
                for (TableRow r : rows) {
                    maxCols = Math.max(maxCols, r.getCells().size());
                }
                
                XWPFTable table = doc.createTable(rows.size(), maxCols);
                
                // Enforce width relative to page dimensions
                double normWidth = tableModel.getBoundingBox() != null ? tableModel.getBoundingBox().getWidth() : 1.0;
                int tableWidthTwips = (int) (normWidth * PAGE_WIDTH_TWIPS);
                
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
                        double cellWidthRatio = modelCell.getBoundingBox() != null ? modelCell.getBoundingBox().getWidth() : 0.2;
                        int cellWidthTwips = (int) (cellWidthRatio * PAGE_WIDTH_TWIPS);
                        
                        // Set cell width (high-level POI API)
                        tableCell.setWidth(String.valueOf(cellWidthTwips));
                    }
                }
                
                // Update cursor to bottom of the table
                double tableHeight = tableModel.getBoundingBox() != null ? tableModel.getBoundingBox().getHeight() : 0.1;
                currentTopTwips = targetTopTwips + (int) (tableHeight * PAGE_HEIGHT_TWIPS);
                
            } else if (element.type == RenderableElement.Type.ARTIFACT) {
                VisionResult.ArtifactInfo artifact = (VisionResult.ArtifactInfo) element.element;
                File file = artifact.getArtifactFile();
                if (!file.exists()) continue;
                
                // Ensure we have an active paragraph to anchor the drawing to
                if (anchorPara == null) {
                    anchorPara = doc.createParagraph();
                }
                
                XWPFRun imgRun = anchorPara.createRun();
                
                BoundingBox box = artifact.getBoundingBox();
                double normWidth = box != null ? box.getWidth() : 0.15;
                double normHeight = box != null ? box.getHeight() : 0.1;
                
                // Map normalized coordinates back to target pixels/inches (96 DPI)
                int targetWidthPx = (int) (normWidth * 8.5 * 96);
                int targetHeightPx = (int) (normHeight * 11 * 96);
                
                int w = Math.max(10, Math.min(600, targetWidthPx));
                int h = Math.max(10, Math.min(800, targetHeightPx));
                
                log.info("Adding visual artifact {} as floating absolute picture at dimensions {}x{}", 
                        file.getName(), w, h);
                
                int pictureType = XWPFDocument.PICTURE_TYPE_PNG;
                if (file.getName().toLowerCase().endsWith(".jpg") || file.getName().toLowerCase().endsWith(".jpeg")) {
                    pictureType = XWPFDocument.PICTURE_TYPE_JPEG;
                }
                
                // 1. Add picture as standard inline element first (generates XML elements)
                try (FileInputStream fis = new FileInputStream(file)) {
                    imgRun.addPicture(fis, 
                            pictureType, 
                            file.getName(), 
                            Units.toEMU(w), 
                            Units.toEMU(h));
                }
                
                // 2. Transform the newly created inline drawing to a page-absolute CTAnchor drawing
                double left = box != null ? box.getLeft() : 0.0;
                double top = box != null ? box.getTop() : 0.0;
                makePictureFloating(imgRun, 0, left, top, Units.toEMU(w), Units.toEMU(h));
            }
        }
    }

    private void makePictureFloating(XWPFRun run, int pictureIndex, double leftRatio, double topRatio, int widthEmu, int heightEmu) {
        try {
            // Retrieve POI's drawing and inline structure
            CTDrawing drawing = run.getCTR().getDrawingArray(pictureIndex);
            CTInline inline = drawing.getInlineArray(0);
            
            // Extract the core graphical object representation
            CTGraphicalObject graphic = inline.getGraphic();
            
            // Build the absolute page floating CTAnchor schema representation
            CTAnchor anchor = CTAnchor.Factory.newInstance();
            
            // Set attributes of anchor
            anchor.setSimplePos2(false); // boolean setter for attribute simplePos
            anchor.setRelativeHeight(251658240); // high z-index overlay priority
            anchor.setBehindDoc(true);           // display behind text layer for absolute readability
            anchor.setLocked(false);
            anchor.setLayoutInCell(true);
            anchor.setAllowOverlap(true);
            
            anchor.setDistT(0);
            anchor.setDistB(0);
            anchor.setDistL(0);
            anchor.setDistR(0);
            
            CTPositiveSize2D extent = anchor.addNewExtent();
            extent.setCx(widthEmu);
            extent.setCy(heightEmu);
            
            CTPoint2D simplePos = anchor.addNewSimplePos();
            simplePos.setX(0);
            simplePos.setY(0);
            
            // Map relative normalized coordinates exactly back to absolute standard Letter EMUs
            // Letter page width is 8.5 in (7,772,400 EMUs) and height is 11 in (10,058,400 EMUs)
            long leftEmu = (long) (leftRatio * 7772400);
            long topEmu = (long) (topRatio * 10058400);
            
            // Horizontal Position relative to PAGE margin boundaries
            CTPosH posH = anchor.addNewPositionH();
            posH.setRelativeFrom(STRelFromH.Enum.forString("page"));
            posH.setPosOffset((int) leftEmu);
            
            // Vertical Position relative to PAGE margin boundaries
            CTPosV posV = anchor.addNewPositionV();
            posV.setRelativeFrom(STRelFromV.Enum.forString("page"));
            posV.setPosOffset((int) topEmu);
            
            anchor.setGraphic(graphic);
            
            anchor.addNewDocPr();
            anchor.getDocPr().setId(inline.getDocPr().getId());
            anchor.getDocPr().setName(inline.getDocPr().getName());
            
            anchor.addNewCNvGraphicFramePr();
            if (inline.getCNvGraphicFramePr() != null) {
                anchor.getCNvGraphicFramePr().setGraphicFrameLocks(inline.getCNvGraphicFramePr().getGraphicFrameLocks());
            }
            
            // Repair corruption by adding the mandatory wrap element
            anchor.addNewWrapNone();
            
            // Overwrite inline drawing inside the Word run structure
            drawing.setAnchorArray(new CTAnchor[]{anchor});
            drawing.removeInline(0);
            
            log.info("Picture successfully converted to floating anchor at EMU offset ({}, {})", leftEmu, topEmu);
            
        } catch (Exception e) {
            log.error("Failed to convert inline image to anchored floating picture", e);
        }
    }

    private void applyRunStyle(XWPFRun run, TextStyle style, String fullTextBlockText) {
        if (style != null) {
            run.setFontFamily(style.getFontName());
            run.setBold(style.isBold());
            run.setItalic(style.isItalic());
            
            // Dynamic Spanish Text-Swell Font Scaling
            double scaledFontSize = style.getFontSize();
            if (fullTextBlockText != null && fullTextBlockText.length() > 100) {
                // Shrink font size slightly if text block is long to prevent swell breakage
                scaledFontSize = Math.max(9.0, style.getFontSize() * 0.9);
            }
            run.setFontSize((int) Math.round(scaledFontSize));
            
            // Format and apply hex colors
            String hex = style.getHexColor() != null ? style.getHexColor().replace("#", "") : "";
            if (hex.length() == 6) {
                run.setColor(hex);
            }
        }
    }
}

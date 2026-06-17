package com.udocmachine.infra.assembler;

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
import com.udocmachine.core.assembler.AssemblyException;
import com.udocmachine.core.assembler.WordAssembler;
import com.udocmachine.core.vision.VisionResult;
import com.udocmachine.model.BoundingBox;
import com.udocmachine.model.DocumentLayout;
import com.udocmachine.model.Table;
import com.udocmachine.model.TableCell;
import com.udocmachine.model.TableRow;
import com.udocmachine.model.TextBlock;
import com.udocmachine.model.TextStyle;

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

    private static class TextLine {
        final double top;
        final double bottom;
        final List<TextBlock> blocks = new ArrayList<>();
        
        TextLine(double top, double bottom) {
            this.top = top;
            this.bottom = bottom;
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
        // 1. Collect and group text blocks into lines to prevent vertical staggering
        List<TextBlock> textBlocks = new ArrayList<>();
        if (layout.getTextBlocks() != null) {
            textBlocks.addAll(layout.getTextBlocks());
        }
        
        // Sort text blocks by their top coordinate first
        textBlocks.sort(Comparator.comparingDouble(b -> b.getBoundingBox().getTop()));
        
        List<TextLine> lines = new ArrayList<>();
        for (TextBlock block : textBlocks) {
            double bTop = block.getBoundingBox().getTop();
            double bHeight = block.getBoundingBox().getHeight();
            double bBottom = bTop + bHeight;
            
            boolean placed = false;
            for (TextLine line : lines) {
                double overlapStart = Math.max(line.top, bTop);
                double overlapEnd = Math.min(line.bottom, bBottom);
                double overlap = overlapEnd - overlapStart;
                
                // Check if this block horizontally overlaps with any block already in the line
                boolean horizontallyOverlaps = false;
                for (TextBlock existing : line.blocks) {
                    double existingLeft = existing.getBoundingBox().getLeft();
                    double existingRight = existingLeft + existing.getBoundingBox().getWidth();
                    double blockLeft = block.getBoundingBox().getLeft();
                    double blockRight = blockLeft + block.getBoundingBox().getWidth();
                    
                    double overlapLeft = Math.max(existingLeft, blockLeft);
                    double overlapRight = Math.min(existingRight, blockRight);
                    double horizOverlap = overlapRight - overlapLeft;
                    
                    double minWidth = Math.min(existing.getBoundingBox().getWidth(), block.getBoundingBox().getWidth());
                    if (horizOverlap > 0 && (horizOverlap / minWidth) > 0.10) {
                        horizontallyOverlaps = true;
                        break;
                    }
                }
                
                // If they overlap vertically by more than 40% AND do not overlap horizontally, place on the same line
                if (overlap > 0 && (overlap / bHeight) > 0.40 && !horizontallyOverlaps) {
                    line.blocks.add(block);
                    placed = true;
                    break;
                }
            }
            
            if (!placed) {
                TextLine newLine = new TextLine(bTop, bBottom);
                newLine.blocks.add(block);
                lines.add(newLine);
            }
        }
        
        // 2. Collect all elements on the page (TextLines, Tables, Artifacts)
        List<RenderableElement> elements = new ArrayList<>();
        
        for (TextLine line : lines) {
            elements.add(new RenderableElement(RenderableElement.Type.TEXT, line.top, line));
        }
        
        if (layout.getTables() != null) {
            for (Table table : layout.getTables()) {
                double top = table.getBoundingBox() != null ? table.getBoundingBox().getTop() : 0.0;
                elements.add(new RenderableElement(RenderableElement.Type.TABLE, top, table));
            }
        }
        
        if (visionResult != null && visionResult.getArtifacts() != null) {
            for (VisionResult.ArtifactInfo artifact : visionResult.getArtifacts()) {
                double top = artifact.getBoundingBox() != null ? artifact.getBoundingBox().getTop() : 0.0;
                elements.add(new RenderableElement(RenderableElement.Type.ARTIFACT, top, artifact));
            }
        }
        
        // Sort all elements dynamically by their vertical position (top coordinate)
        elements.sort(Comparator.comparingDouble(e -> e.top));
        
        // Track the current active paragraph to anchor floating images to
        XWPFParagraph anchorPara = null;
        
        // Track absolute vertical cursor in Twips relative to physical page top
        int currentTopTwips = 0;
        
        // 3. Sequentially reconstruct elements in their natural vertical order
        for (RenderableElement element : elements) {
            if (element.type == RenderableElement.Type.TEXT) {
                TextLine line = (TextLine) element.element;
                XWPFParagraph paragraph = doc.createParagraph();
                anchorPara = paragraph; // update active anchor paragraph
                
                // Sort blocks on this line left-to-right
                line.blocks.sort(Comparator.comparingDouble(b -> b.getBoundingBox().getLeft()));
                
                // Set absolute vertical positioning using spacing before
                double targetTop = line.top;
                int targetTopTwips = (int) (targetTop * PAGE_HEIGHT_TWIPS);
                int spacingBefore = targetTopTwips - currentTopTwips;
                paragraph.setSpacingBefore(Math.max(0, spacingBefore));
                paragraph.setSpacingAfter(0); // eliminate default swelling
                
                // Update cursor to bottom of the line
                double lineHeight = line.bottom - line.top;
                currentTopTwips = targetTopTwips + (int) (lineHeight * PAGE_HEIGHT_TWIPS);
                
                // Set first block's indentation
                TextBlock firstBlock = line.blocks.get(0);
                double firstLeft = firstBlock.getBoundingBox().getLeft();
                int leftIndentTwips = (int) (firstLeft * PAGE_WIDTH_TWIPS);
                paragraph.setIndentationLeft(Math.max(0, leftIndentTwips));
                
                double currentRight = firstLeft + firstBlock.getBoundingBox().getWidth();
                
                for (int bIdx = 0; bIdx < line.blocks.size(); bIdx++) {
                    TextBlock block = line.blocks.get(bIdx);
                    
                    // If not the first block, add spaces to push it horizontally
                    if (bIdx > 0) {
                        double blockLeft = block.getBoundingBox().getLeft();
                        double gap = blockLeft - currentRight;
                        if (gap > 0.01) {
                            int gapTwips = (int) (gap * PAGE_WIDTH_TWIPS);
                            int numSpaces = Math.max(1, gapTwips / 100); // 1 space = ~100 twips in Calibri 11pt
                            XWPFRun spaceRun = paragraph.createRun();
                            StringBuilder sb = new StringBuilder();
                            for (int s = 0; s < numSpaces; s++) {
                                sb.append(" ");
                            }
                            spaceRun.setText(sb.toString());
                            preserveSpaces(spaceRun);
                        }
                        currentRight = blockLeft + block.getBoundingBox().getWidth();
                    }
                    
                    // Render the block's styled inline runs
                    List<com.udocmachine.model.InlineRun> inlineRuns = block.getInlineRuns();
                    if (inlineRuns == null || inlineRuns.isEmpty()) {
                        XWPFRun run = paragraph.createRun();
                        String text = block.getText();
                        if (text != null) {
                            run.setText(text.replace("\n", " ").replace("\r", ""));
                        }
                        preserveSpaces(run);
                        applyRunStyle(run, block.getTextStyle(), block.getText());
                    } else {
                        for (com.udocmachine.model.InlineRun inlineRun : inlineRuns) {
                            XWPFRun run = paragraph.createRun();
                            String text = inlineRun.getText();
                            if (text != null) {
                                run.setText(text.replace("\n", " ").replace("\r", ""));
                            }
                            preserveSpaces(run);
                            applyRunStyle(run, block.getTextStyle(), block.getText());
                            if (inlineRun.isBold()) {
                                run.setBold(true);
                            }
                            if (inlineRun.isItalic()) {
                                run.setItalic(true);
                            }
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
                
                // Configure beautiful, clean solid black table borders (0.5 pt / 4 twips)
                table.setInsideHBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
                table.setInsideVBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
                table.setLeftBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
                table.setRightBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
                table.setTopBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
                table.setBottomBorder(org.apache.poi.xwpf.usermodel.XWPFTable.XWPFBorderType.SINGLE, 4, 0, "000000");
                
                // Enforce width relative to page dimensions
                double normWidth = tableModel.getBoundingBox() != null ? tableModel.getBoundingBox().getWidth() : 1.0;
                int tableWidthTwips = (int) (normWidth * PAGE_WIDTH_TWIPS);
                table.setWidth(tableWidthTwips);
    
                // Populate cells with clean styling
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
                        
                        // Style the cell content center-aligned with Calibri 10pt
                        XWPFParagraph cellPara = tableCell.getParagraphs().get(0);
                        if (cellPara == null) {
                            cellPara = tableCell.addParagraph();
                        }
                        cellPara.setAlignment(ParagraphAlignment.CENTER);
                        cellPara.setSpacingAfter(0);
                        cellPara.setSpacingBefore(0);
                        
                        XWPFRun cellRun = cellPara.createRun();
                        cellRun.setText(modelCell.getText());
                        cellRun.setFontFamily("Calibri");
                        cellRun.setFontSize(10);
                        
                        // Bolding the header row
                        if (rIdx == 0) {
                            cellRun.setBold(true);
                        }
                        
                        // Width configuration for individual cells
                        double cellWidthRatio = modelCell.getBoundingBox() != null ? modelCell.getBoundingBox().getWidth() : 0.2;
                        int cellWidthTwips = (int) (cellWidthRatio * PAGE_WIDTH_TWIPS);
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

    private void preserveSpaces(XWPFRun run) {
        if (run != null && run.getCTR() != null) {
            for (org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText ctText : run.getCTR().getTList()) {
                org.w3c.dom.Node node = ctText.getDomNode();
                if (node instanceof org.w3c.dom.Element) {
                    ((org.w3c.dom.Element) node).setAttribute("xml:space", "preserve");
                }
            }
        }
    }
}

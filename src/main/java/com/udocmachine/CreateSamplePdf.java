package com.udocmachine;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import java.awt.Color;
import java.io.File;
import java.io.IOException;

public class CreateSamplePdf {
    public static void main(String[] args) {
        System.out.println("Generating sample PDF...");
        File dir = new File("samples");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File pdfFile = new File(dir, "scanned_document.pdf");
        
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                // Add some text
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
                contentStream.newLineAtOffset(100, 700);
                contentStream.showText("OFFICIAL DOCUMENT CERTIFICATE");
                contentStream.endText();
                
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(100, 650);
                contentStream.showText("This certifies that the digital artifacts generated inside this workspace");
                contentStream.newLineAtOffset(0, -20);
                contentStream.showText("comply with high-fidelity formatting standards.");
                contentStream.endText();

                // Draw a colored stamp (blue rectangle)
                contentStream.setNonStrokingColor(Color.BLUE);
                contentStream.addRect(100, 300, 150, 70);
                contentStream.fill();
                
                // Draw some text inside the stamp (in white)
                contentStream.beginText();
                contentStream.setNonStrokingColor(Color.WHITE);
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 10);
                contentStream.newLineAtOffset(110, 330);
                contentStream.showText("VERIFIED & APPROVED");
                contentStream.endText();

                // Draw a red signature (a line or simple curve)
                contentStream.setStrokingColor(Color.RED);
                contentStream.setLineWidth(3f);
                contentStream.moveTo(400, 300);
                contentStream.lineTo(500, 350);
                contentStream.stroke();
                
                contentStream.beginText();
                contentStream.setNonStrokingColor(Color.RED);
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 8);
                contentStream.newLineAtOffset(420, 280);
                contentStream.showText("Nacho (Developer)");
                contentStream.endText();
            }
            
            document.save(pdfFile);
            System.out.println("Sample PDF generated successfully at: " + pdfFile.getAbsolutePath());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

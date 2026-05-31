package com.taikatranslator.model;

public class TextStyle {
    private String fontName = "Calibri";
    private double fontSize = 11.0;
    private boolean bold = false;
    private boolean italic = false;
    private String hexColor = "#000000";

    public TextStyle() {}

    public TextStyle(String fontName, double fontSize, boolean bold, boolean italic, String hexColor) {
        this.fontName = fontName;
        this.fontSize = fontSize;
        this.bold = bold;
        this.italic = italic;
        this.hexColor = hexColor;
    }

    public String getFontName() {
        return fontName;
    }

    public void setFontName(String fontName) {
        this.fontName = fontName;
    }

    public double getFontSize() {
        return fontSize;
    }

    public void setFontSize(double fontSize) {
        this.fontSize = fontSize;
    }

    public boolean isBold() {
        return bold;
    }

    public void setBold(boolean bold) {
        this.bold = bold;
    }

    public boolean isItalic() {
        return italic;
    }

    public void setItalic(boolean italic) {
        this.italic = italic;
    }

    public String getHexColor() {
        return hexColor;
    }

    public void setHexColor(String hexColor) {
        this.hexColor = hexColor;
    }

    @Override
    public String toString() {
        return String.format("TextStyle[font=%s, size=%.1f, bold=%b, italic=%b, color=%s]", 
                fontName, fontSize, bold, italic, hexColor);
    }
}

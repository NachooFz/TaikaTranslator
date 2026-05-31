package com.taikatranslator.model;

public class InlineRun {
    private String text;
    private boolean bold;
    private boolean italic;

    public InlineRun() {}

    public InlineRun(String text, boolean bold, boolean italic) {
        this.text = text;
        this.bold = bold;
        this.italic = italic;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
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

    @Override
    public String toString() {
        return String.format("InlineRun[text='%s', bold=%b, italic=%b]", text, bold, italic);
    }
}

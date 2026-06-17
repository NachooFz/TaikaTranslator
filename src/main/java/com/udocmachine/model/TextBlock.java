package com.udocmachine.model;

import java.util.ArrayList;
import java.util.List;

public class TextBlock {
    private String text;
    private BoundingBox boundingBox;
    private TextStyle textStyle;
    private int readingOrder;
    private List<InlineRun> inlineRuns = new ArrayList<>();

    public TextBlock() {}

    public TextBlock(String text, BoundingBox boundingBox, TextStyle textStyle, int readingOrder) {
        this.text = text;
        this.boundingBox = boundingBox;
        this.textStyle = textStyle;
        this.readingOrder = readingOrder;
    }

    public List<InlineRun> getInlineRuns() {
        return inlineRuns;
    }

    public void setInlineRuns(List<InlineRun> inlineRuns) {
        this.inlineRuns = inlineRuns;
    }

    public void addInlineRun(InlineRun run) {
        if (this.inlineRuns == null) {
            this.inlineRuns = new ArrayList<>();
        }
        this.inlineRuns.add(run);
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public void setBoundingBox(BoundingBox boundingBox) {
        this.boundingBox = boundingBox;
    }

    public TextStyle getTextStyle() {
        return textStyle;
    }

    public void setTextStyle(TextStyle textStyle) {
        this.textStyle = textStyle;
    }

    public int getReadingOrder() {
        return readingOrder;
    }

    public void setReadingOrder(int readingOrder) {
        this.readingOrder = readingOrder;
    }

    @Override
    public String toString() {
        return String.format("TextBlock[text='%s', order=%d, bounds=%s, style=%s]", 
                text.length() > 20 ? text.substring(0, 20) + "..." : text, 
                readingOrder, boundingBox, textStyle);
    }
}

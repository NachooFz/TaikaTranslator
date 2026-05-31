package com.taikatranslator.model;

public class TextBlock {
    private String text;
    private BoundingBox boundingBox;
    private TextStyle textStyle;
    private int readingOrder;

    public TextBlock() {}

    public TextBlock(String text, BoundingBox boundingBox, TextStyle textStyle, int readingOrder) {
        this.text = text;
        this.boundingBox = boundingBox;
        this.textStyle = textStyle;
        this.readingOrder = readingOrder;
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

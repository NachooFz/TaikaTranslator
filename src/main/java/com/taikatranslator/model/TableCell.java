package com.taikatranslator.model;

public class TableCell {
    private String text;
    private BoundingBox boundingBox;
    private int rowIndex;
    private int columnIndex;
    private int rowSpan = 1;
    private int colSpan = 1;

    public TableCell() {}

    public TableCell(String text, BoundingBox boundingBox, int rowIndex, int columnIndex, int rowSpan, int colSpan) {
        this.text = text;
        this.boundingBox = boundingBox;
        this.rowIndex = rowIndex;
        this.columnIndex = columnIndex;
        this.rowSpan = rowSpan;
        this.colSpan = colSpan;
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

    public int getRowIndex() {
        return rowIndex;
    }

    public void setRowIndex(int rowIndex) {
        this.rowIndex = rowIndex;
    }

    public int getColumnIndex() {
        return columnIndex;
    }

    public void setColumnIndex(int columnIndex) {
        this.columnIndex = columnIndex;
    }

    public int getRowSpan() {
        return rowSpan;
    }

    public void setRowSpan(int rowSpan) {
        this.rowSpan = rowSpan;
    }

    public int getColSpan() {
        return colSpan;
    }

    public void setColSpan(int colSpan) {
        this.colSpan = colSpan;
    }

    @Override
    public String toString() {
        return String.format("TableCell[row=%d, col=%d, span=(%d,%d), text='%s']", 
                rowIndex, columnIndex, rowSpan, colSpan, 
                text.length() > 15 ? text.substring(0, 15) + "..." : text);
    }
}

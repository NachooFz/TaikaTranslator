package com.taikatranslator.model;

import java.util.ArrayList;
import java.util.List;

public class DocumentLayout {
    private double pageWidth;
    private double pageHeight;
    private List<TextBlock> textBlocks = new ArrayList<>();
    private List<Table> tables = new ArrayList<>();

    public DocumentLayout() {}

    public DocumentLayout(double pageWidth, double pageHeight) {
        this.pageWidth = pageWidth;
        this.pageHeight = pageHeight;
    }

    public double getPageWidth() {
        return pageWidth;
    }

    public void setPageWidth(double pageWidth) {
        this.pageWidth = pageWidth;
    }

    public double getPageHeight() {
        return pageHeight;
    }

    public void setPageHeight(double pageHeight) {
        this.pageHeight = pageHeight;
    }

    public List<TextBlock> getTextBlocks() {
        return textBlocks;
    }

    public void setTextBlocks(List<TextBlock> textBlocks) {
        this.textBlocks = textBlocks;
    }

    public void addTextBlock(TextBlock textBlock) {
        this.textBlocks.add(textBlock);
    }

    public List<Table> getTables() {
        return tables;
    }

    public void setTables(List<Table> tables) {
        this.tables = tables;
    }

    public void addTable(Table table) {
        this.tables.add(table);
    }

    @Override
    public String toString() {
        return String.format("DocumentLayout[page=%.2fx%.2f, textBlocks=%d, tables=%d]", 
                pageWidth, pageHeight, textBlocks.size(), tables.size());
    }
}

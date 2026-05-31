package com.taikatranslator.model;

import java.util.ArrayList;
import java.util.List;

public class Table {
    private BoundingBox boundingBox;
    private List<TableRow> rows = new ArrayList<>();

    public Table() {}

    public Table(BoundingBox boundingBox, List<TableRow> rows) {
        this.boundingBox = boundingBox;
        this.rows = rows;
    }

    public BoundingBox getBoundingBox() {
        return boundingBox;
    }

    public void setBoundingBox(BoundingBox boundingBox) {
        this.boundingBox = boundingBox;
    }

    public List<TableRow> getRows() {
        return rows;
    }

    public void setRows(List<TableRow> rows) {
        this.rows = rows;
    }

    public void addRow(TableRow row) {
        this.rows.add(row);
    }

    @Override
    public String toString() {
        return String.format("Table[rowsCount=%d, bounds=%s]", rows.size(), boundingBox);
    }
}

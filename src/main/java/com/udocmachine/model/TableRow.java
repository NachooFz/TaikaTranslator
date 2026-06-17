package com.udocmachine.model;

import java.util.ArrayList;
import java.util.List;

public class TableRow {
    private List<TableCell> cells = new ArrayList<>();

    public TableRow() {}

    public TableRow(List<TableCell> cells) {
        this.cells = cells;
    }

    public List<TableCell> getCells() {
        return cells;
    }

    public void setCells(List<TableCell> cells) {
        this.cells = cells;
    }

    public void addCell(TableCell cell) {
        this.cells.add(cell);
    }

    @Override
    public String toString() {
        return String.format("TableRow[cellsCount=%d]", cells.size());
    }
}

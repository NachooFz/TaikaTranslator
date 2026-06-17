package com.udocmachine.model;

public class BoundingBox {
    private double top;
    private double left;
    private double width;
    private double height;

    public BoundingBox() {}

    public BoundingBox(double top, double left, double width, double height) {
        this.top = top;
        this.left = left;
        this.width = width;
        this.height = height;
    }

    public double getTop() {
        return top;
    }

    public void setTop(double top) {
        this.top = top;
    }

    public double getLeft() {
        return left;
    }

    public void setLeft(double left) {
        this.left = left;
    }

    public double getWidth() {
        return width;
    }

    public void setWidth(double width) {
        this.width = width;
    }

    public double getHeight() {
        return height;
    }

    public void setHeight(double height) {
        this.height = height;
    }

    @Override
    public String toString() {
        return String.format("BoundingBox[top=%.4f, left=%.4f, width=%.4f, height=%.4f]", top, left, width, height);
    }
}

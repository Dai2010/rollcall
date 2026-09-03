package com.dai2010.rollcall.ui;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A lightweight rounded container used for the name and result surfaces. */
public class RoundedPanel extends JPanel {
    private final int radius;
    private Color fillColor;
    private Color borderColor;

    public RoundedPanel() {
        this(16, new Color(255, 255, 255, 210), new Color(190, 205, 215, 180));
    }

    public RoundedPanel(int radius, Color fillColor, Color borderColor) {
        this.radius = radius;
        this.fillColor = fillColor;
        this.borderColor = borderColor;
        setOpaque(false);
        setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
    }

    public void setFillColor(Color fillColor) {
        this.fillColor = fillColor;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int width = getWidth() - 1;
        int height = getHeight() - 1;
        g.setColor(fillColor);
        g.fillRoundRect(0, 0, width, height, radius, radius);
        if (borderColor != null) {
            g.setColor(borderColor);
            g.drawRoundRect(0, 0, width, height, radius, radius);
        }
        g.dispose();
    }
}

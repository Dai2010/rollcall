package com.dai2010.rollcall.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** A consistent translucent blue rounded label for every displayed name. */
public final class NameChip extends JLabel {
    private static final Color FILL = new Color(166, 218, 241, 150);
    private static final Color BORDER = new Color(90, 157, 190, 150);

    public NameChip(String text) {
        super(text);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        setForeground(new Color(27, 63, 82));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(FILL);
        g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
        g.setColor(BORDER);
        g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
        g.dispose();
        super.paintComponent(graphics);
    }
}

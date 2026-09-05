package com.dai2010.rollcall.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.Objects;

/** A consistent translucent blue rounded label for every displayed name. */
public final class NameChip extends JLabel {
    private static final Color FILL = new Color(166, 218, 241, 150);
    private static final Color BORDER = new Color(90, 157, 190, 150);
    private static final String ELLIPSIS = "...";
    private final String fullText;

    public NameChip(String text) {
        super(Objects.requireNonNullElse(text, ""));
        fullText = Objects.requireNonNullElse(text, "");
        putClientProperty("html.disable", Boolean.TRUE);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
        setForeground(new Color(27, 63, 82));
    }

    void fitToWidth(int maximumWidth) {
        Insets insets = getInsets();
        int textWidth = Math.max(0, maximumWidth - insets.left - insets.right);
        FontMetrics metrics = getFontMetrics(getFont());
        if (metrics.stringWidth(fullText) <= textWidth) {
            setText(fullText);
            setToolTipText(null);
            return;
        }

        setText(abbreviate(fullText, metrics, textWidth));
        setToolTipText(fullText);
    }

    private static String abbreviate(String value, FontMetrics metrics, int maximumWidth) {
        if (maximumWidth <= 0 || metrics.stringWidth(ELLIPSIS) > maximumWidth) {
            return "";
        }
        int low = 0;
        int high = value.codePointCount(0, value.length());
        while (low < high) {
            int count = (low + high + 1) / 2;
            int end = value.offsetByCodePoints(0, count);
            if (metrics.stringWidth(value.substring(0, end) + ELLIPSIS) <= maximumWidth) {
                low = count;
            } else {
                high = count - 1;
            }
        }
        int end = value.offsetByCodePoints(0, low);
        return value.substring(0, end) + ELLIPSIS;
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

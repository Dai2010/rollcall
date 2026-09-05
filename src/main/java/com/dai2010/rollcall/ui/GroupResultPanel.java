package com.dai2010.rollcall.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/** Lays out one result group with wrapped names aligned after the group label. */
final class GroupResultPanel extends JPanel {
    private static final int DEFAULT_LAYOUT_WIDTH = 640;
    private static final int LABEL_GAP = 12;
    private static final int CHIP_GAP = 8;
    private static final int ROW_GAP = 8;

    private final JLabel groupLabel;
    private final List<NameChip> nameChips = new ArrayList<>();
    private int labelColumnWidth;

    GroupResultPanel(String label, List<String> names) {
        setLayout(null);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));

        if (label == null || label.isBlank()) {
            groupLabel = null;
        } else {
            groupLabel = new JLabel(label);
            groupLabel.setFont(groupLabel.getFont().deriveFont(Font.BOLD));
            labelColumnWidth = groupLabel.getPreferredSize().width;
            add(groupLabel);
        }

        for (String name : names) {
            NameChip chip = new NameChip(name);
            nameChips.add(chip);
            add(chip);
        }
    }

    int naturalLabelWidth() {
        return groupLabel == null ? 0 : groupLabel.getPreferredSize().width;
    }

    void setLabelColumnWidth(int width) {
        labelColumnWidth = Math.max(naturalLabelWidth(), width);
        revalidate();
    }

    List<NameChip> nameChips() {
        return List.copyOf(nameChips);
    }

    int preferredHeightForWidth(int width) {
        return layoutForWidth(Math.max(1, width), false);
    }

    @Override
    public Dimension getPreferredSize() {
        int width = resolvedLayoutWidth();
        return new Dimension(width, preferredHeightForWidth(width));
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, getPreferredSize().height);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }

    @Override
    public void doLayout() {
        layoutForWidth(Math.max(1, getWidth()), true);
    }

    private int resolvedLayoutWidth() {
        if (getParent() != null && getParent().getWidth() > 0) {
            return getParent().getWidth();
        }
        if (getWidth() > 0) {
            return getWidth();
        }
        return DEFAULT_LAYOUT_WIDTH;
    }

    private int layoutForWidth(int width, boolean applyBounds) {
        Insets insets = getInsets();
        int contentRight = Math.max(insets.left + 1, width - insets.right);
        Dimension labelSize = groupLabel == null ? new Dimension() : groupLabel.getPreferredSize();
        int labelWidth = groupLabel == null ? 0 : Math.min(labelColumnWidth, contentRight - insets.left);
        int namesX = groupLabel == null
                ? insets.left
                : Math.min(contentRight, insets.left + labelWidth + LABEL_GAP);
        int availableNameWidth = Math.max(1, contentRight - namesX);

        int x = namesX;
        int y = insets.top;
        int rowHeight = labelSize.height;
        int firstRowHeight = 0;
        boolean firstRow = true;

        for (NameChip chip : nameChips) {
            chip.fitToWidth(availableNameWidth);
            Dimension preferred = chip.getPreferredSize();
            int chipWidth = Math.min(preferred.width, availableNameWidth);
            if (x > namesX && x + chipWidth > contentRight) {
                firstRowHeight = firstRow ? rowHeight : firstRowHeight;
                firstRow = false;
                y += rowHeight + ROW_GAP;
                x = namesX;
                rowHeight = 0;
            }
            if (applyBounds) {
                chip.setBounds(x, y, chipWidth, preferred.height);
            }
            x += chipWidth + CHIP_GAP;
            rowHeight = Math.max(rowHeight, preferred.height);
        }

        if (firstRowHeight == 0) {
            firstRowHeight = rowHeight;
        }
        if (applyBounds && groupLabel != null) {
            int labelY = insets.top + Math.max(0, (firstRowHeight - labelSize.height) / 2);
            groupLabel.setBounds(insets.left, labelY, labelWidth, labelSize.height);
        }
        return y + Math.max(1, rowHeight) + insets.bottom;
    }
}

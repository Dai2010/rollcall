package com.dai2010.rollcall.ui;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JViewport;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/** A vertically scrolling result list whose width always follows its viewport. */
final class ResultsPanel extends JPanel implements Scrollable {
    private static final int DEFAULT_LAYOUT_WIDTH = 640;
    static final int TOUCH_SCROLL_INCREMENT = 48;
    static final int TOUCH_SCROLLBAR_WIDTH = 24;

    ResultsPanel() {
        setLayout(null);
        setOpaque(false);
    }

    @Override
    public Dimension getPreferredSize() {
        int width = resolvedLayoutWidth();
        int height = 0;
        for (Component component : getComponents()) {
            if (component.isVisible()) {
                height += preferredHeight(component, width);
            }
        }
        return new Dimension(width, height);
    }

    @Override
    public void doLayout() {
        int width = Math.max(1, getWidth());
        int y = 0;
        for (Component component : getComponents()) {
            if (!component.isVisible()) {
                continue;
            }
            int height = preferredHeight(component, width);
            component.setBounds(0, y, width, height);
            y += height;
        }
    }

    JScrollPane createScrollPane() {
        JScrollPane scrollPane = new JScrollPane(this,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(TOUCH_SCROLL_INCREMENT);
        scrollPane.getVerticalScrollBar().setBlockIncrement(TOUCH_SCROLL_INCREMENT * 4);
        Dimension preferred = scrollPane.getVerticalScrollBar().getPreferredSize();
        scrollPane.getVerticalScrollBar().setPreferredSize(
                new Dimension(Math.max(TOUCH_SCROLLBAR_WIDTH, preferred.width), preferred.height));
        scrollPane.getViewport().addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                ResultsPanel.this.revalidate();
                ResultsPanel.this.repaint();
            }
        });
        return scrollPane;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return TOUCH_SCROLL_INCREMENT;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return Math.max(TOUCH_SCROLL_INCREMENT, visibleRect.height - TOUCH_SCROLL_INCREMENT);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    private int resolvedLayoutWidth() {
        if (getParent() instanceof JViewport viewport && viewport.getExtentSize().width > 0) {
            return viewport.getExtentSize().width;
        }
        return getWidth() > 0 ? getWidth() : DEFAULT_LAYOUT_WIDTH;
    }

    private int preferredHeight(Component component, int width) {
        if (component instanceof GroupResultPanel group) {
            return group.preferredHeightForWidth(width);
        }
        return component.getPreferredSize().height;
    }
}

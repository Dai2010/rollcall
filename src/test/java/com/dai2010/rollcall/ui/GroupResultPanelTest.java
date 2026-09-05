package com.dai2010.rollcall.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupResultPanelTest {
    @Test
    void wrapsNamesWithAConsistentHangingIndent() throws Exception {
        runOnEventThread(() -> {
            GroupResultPanel panel = new GroupResultPanel("第 1 组",
                    List.of("学生一号", "学生二号", "学生三号", "学生四号", "学生五号"));
            panel.setSize(260, 400);
            panel.doLayout();

            List<NameChip> chips = panel.nameChips();
            int firstNamesX = chips.get(0).getX();
            int firstRowY = chips.get(0).getY();
            Insets insets = panel.getInsets();
            Set<Integer> encounteredRows = new HashSet<>();
            assertTrue(chips.stream().anyMatch(chip -> chip.getY() > firstRowY));
            for (NameChip chip : chips) {
                if (encounteredRows.add(chip.getY())) {
                    assertEquals(firstNamesX, chip.getX());
                }
            }
            assertTrue(chips.stream().allMatch(chip -> chip.getX() + chip.getWidth()
                    <= panel.getWidth() - insets.right));
            assertTrue(panel.getComponent(0).getX() < firstNamesX);
        });
    }

    @Test
    void growsVerticallyAsTheViewportNarrows() throws Exception {
        runOnEventThread(() -> {
            GroupResultPanel panel = new GroupResultPanel("第 12 组",
                    List.of("学生一号", "学生二号", "学生三号", "学生四号", "学生五号"));
            panel.setSize(460, 400);
            int wideHeight = panel.getPreferredSize().height;
            panel.setSize(220, 400);
            int narrowHeight = panel.getPreferredSize().height;

            assertTrue(narrowHeight > wideHeight);
        });
    }

    @Test
    void abbreviatesAnOversizedNameWithoutOverflowing() throws Exception {
        runOnEventThread(() -> {
            String fullName = "这是一个长度明显超过结果区域可用宽度的姓名";
            GroupResultPanel panel = new GroupResultPanel("第 1 组", List.of(fullName));
            panel.setSize(190, 200);
            panel.doLayout();

            NameChip chip = panel.nameChips().get(0);
            assertTrue(chip.getX() + chip.getWidth() <= panel.getWidth() - panel.getInsets().right);
            assertTrue(chip.getText().endsWith("..."));
            assertEquals(fullName, chip.getToolTipText());
        });
    }

    @Test
    void keepsOnlyTheTouchFriendlyVerticalScrollbar() throws Exception {
        runOnEventThread(() -> {
            ResultsPanel results = new ResultsPanel();
            JScrollPane scrollPane = results.createScrollPane();

            assertEquals(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, scrollPane.getVerticalScrollBarPolicy());
            assertEquals(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER, scrollPane.getHorizontalScrollBarPolicy());
            assertTrue(scrollPane.getVerticalScrollBar().getPreferredSize().width
                    >= ResultsPanel.TOUCH_SCROLLBAR_WIDTH);
            assertTrue(results.getScrollableTracksViewportWidth());
            assertFalse(results.getScrollableTracksViewportHeight());
            assertEquals(ResultsPanel.TOUCH_SCROLL_INCREMENT,
                    scrollPane.getVerticalScrollBar().getUnitIncrement());
        });
    }

    @Test
    void stacksEachGroupInItsOwnVerticalSection() throws Exception {
        runOnEventThread(() -> {
            ResultsPanel results = new ResultsPanel();
            GroupResultPanel first = new GroupResultPanel("第 1 组",
                    List.of("张三", "李四", "王五", "赵六"));
            GroupResultPanel second = new GroupResultPanel("第 2 组",
                    List.of("钱七", "孙八", "周九", "吴十"));
            results.add(first);
            results.add(second);
            results.setSize(280, 1);
            results.setSize(280, results.getPreferredSize().height);
            results.doLayout();
            first.doLayout();
            second.doLayout();

            assertEquals(0, first.getY());
            assertEquals(first.getHeight(), second.getY());
            assertEquals(results.getWidth(), first.getWidth());
            assertEquals(results.getWidth(), second.getWidth());
            assertTrue(first.nameChips().stream()
                    .allMatch(chip -> chip.getY() + chip.getHeight() <= first.getHeight()));
            assertTrue(second.nameChips().stream()
                    .allMatch(chip -> chip.getY() + chip.getHeight() <= second.getHeight()));
        });
    }

    @Test
    void keepsEveryWrappedNameInsideARealScrollViewport() throws Exception {
        runOnEventThread(() -> {
            ResultsPanel results = new ResultsPanel();
            List<GroupResultPanel> groups = java.util.stream.IntStream.rangeClosed(1, 6)
                    .mapToObj(number -> new GroupResultPanel("第 " + number + " 组", List.of(
                            "学生一号", "学生二号", "学生三号", "学生四号", "学生五号",
                            "学生六号", "学生七号", "学生八号", "很长的学生姓名用于检查换行")))
                    .toList();
            int labelWidth = groups.stream().mapToInt(GroupResultPanel::naturalLabelWidth).max().orElse(0);
            for (GroupResultPanel group : groups) {
                group.setLabelColumnWidth(labelWidth);
                results.add(group);
            }

            JScrollPane scrollPane = results.createScrollPane();
            scrollPane.setSize(380, 270);
            layoutTree(scrollPane);
            layoutTree(scrollPane);

            assertEquals(scrollPane.getViewport().getExtentSize().width, results.getWidth());
            assertTrue(scrollPane.getVerticalScrollBar().isVisible());
            assertTrue(scrollPane.getVerticalScrollBar().getMaximum()
                    > scrollPane.getVerticalScrollBar().getVisibleAmount());
            for (GroupResultPanel group : groups) {
                assertTrue(group.nameChips().stream()
                        .allMatch(chip -> chip.getY() + chip.getHeight() <= group.getHeight()));
            }
        });
    }

    private void layoutTree(Container container) {
        container.doLayout();
        for (Component component : container.getComponents()) {
            if (component instanceof Container child) {
                layoutTree(child);
            }
        }
    }

    private void runOnEventThread(Runnable assertion) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                assertion.run();
            } catch (Throwable error) {
                failure.set(error);
            }
        });
        Throwable error = failure.get();
        if (error != null) {
            throw new AssertionError(error);
        }
    }
}

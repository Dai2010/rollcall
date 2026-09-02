package com.dai2010.rollcall;

import com.dai2010.rollcall.ui.MainFrame;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // The cross-platform Swing look and feel remains available.
            }
            MainFrame frame = new MainFrame();
            frame.setVisible(true);
        });
    }
}

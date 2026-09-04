package com.dai2010.rollcall;

import com.dai2010.rollcall.ui.MainFrame;

import javax.swing.JOptionPane;
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
            try {
                MainFrame frame = new MainFrame();
                frame.setVisible(true);
                frame.startAutomaticUpdateCheck();
            } catch (RuntimeException error) {
                error.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        "点名助手启动失败：" + (error.getMessage() == null
                                ? error.getClass().getSimpleName() : error.getMessage()),
                        "启动失败", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}

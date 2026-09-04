package com.dai2010.rollcall.ui;

import com.dai2010.rollcall.service.UpdateInstaller;
import com.dai2010.rollcall.service.UpdateService;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.Window;
import java.awt.event.WindowEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.net.URI;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutionException;

/** Update status view backed by the public GitHub Releases API. */
final class UpdatePanel extends JPanel {
    private static final Color SURFACE_BACKGROUND = new Color(255, 255, 255);
    private static final Color TEXT_COLOR = new Color(28, 55, 69);
    private static final Color MUTED_TEXT_COLOR = new Color(83, 111, 124);
    private static final Color CONTROL_BORDER = new Color(164, 193, 205);
    private static final Color ACCENT_COLOR = new Color(48, 126, 160);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final UpdateService updateService;
    private final UpdateInstaller updateInstaller;
    private final String currentVersion;
    private final JLabel currentVersionValue = versionLabel();
    private final JLabel latestVersionValue = versionLabel();
    private final JLabel statusLabel = new JLabel("等待检查");
    private final JTextArea notesArea = new JTextArea();
    private final JButton checkButton = createButton("重新检查");
    private final JButton releaseButton = createButton("打开发布页");
    private final JButton installButton = createButton("代理下载并安装");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private URI releasePage;
    private UpdateService.ReleaseInfo latestRelease;
    private boolean automaticCheckStarted;

    UpdatePanel(UpdateService updateService, UpdateInstaller updateInstaller, String currentVersion) {
        super(new BorderLayout(14, 14));
        this.updateService = updateService;
        this.updateInstaller = updateInstaller;
        this.currentVersion = currentVersion;
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
        build();
    }

    void startAutomaticCheck() {
        if (automaticCheckStarted) {
            return;
        }
        automaticCheckStarted = true;
        checkForUpdates();
    }

    private void build() {
        JPanel header = new JPanel(new BorderLayout(0, 4));
        header.setOpaque(false);
        JLabel title = new JLabel("软件更新");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setForeground(TEXT_COLOR);
        JLabel subtitle = new JLabel("启动时自动查询 GitHub 上最新发布的正式版本");
        subtitle.setForeground(MUTED_TEXT_COLOR);
        header.add(title, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setOpaque(false);
        RoundedPanel versions = new RoundedPanel(16, SURFACE_BACKGROUND, CONTROL_BORDER);
        versions.setLayout(new GridLayout(1, 2, 12, 0));
        currentVersionValue.setText("v" + currentVersion);
        latestVersionValue.setText("--");
        versions.add(versionBlock("当前版本", currentVersionValue));
        versions.add(versionBlock("最新版本", latestVersionValue));
        content.add(versions, BorderLayout.NORTH);

        JPanel notes = new JPanel(new BorderLayout(0, 8));
        notes.setOpaque(false);
        JLabel notesTitle = new JLabel("更新信息");
        notesTitle.setFont(notesTitle.getFont().deriveFont(Font.BOLD));
        notesTitle.setForeground(TEXT_COLOR);
        notes.add(notesTitle, BorderLayout.NORTH);
        notesArea.setEditable(false);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setForeground(TEXT_COLOR);
        notesArea.setBackground(SURFACE_BACKGROUND);
        notesArea.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        notesArea.setText("应用启动后会自动检查更新。也可以随时点击“重新检查”。");
        JScrollPane notesScroll = new JScrollPane(notesArea);
        notesScroll.setBorder(BorderFactory.createLineBorder(CONTROL_BORDER));
        notes.add(notesScroll, BorderLayout.CENTER);
        content.add(notes, BorderLayout.CENTER);
        add(content, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setOpaque(false);
        JPanel statusArea = new JPanel(new BorderLayout(0, 5));
        statusArea.setOpaque(false);
        statusLabel.setForeground(MUTED_TEXT_COLOR);
        statusArea.add(statusLabel, BorderLayout.NORTH);
        progressBar.setStringPainted(true);
        progressBar.setString("等待下载");
        progressBar.setValue(0);
        statusArea.add(progressBar, BorderLayout.SOUTH);
        footer.add(statusArea, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        checkButton.addActionListener(event -> checkForUpdates());
        releaseButton.setEnabled(false);
        releaseButton.addActionListener(event -> openReleasePage());
        installButton.setEnabled(false);
        installButton.addActionListener(event -> downloadAndInstall());
        actions.add(checkButton);
        actions.add(releaseButton);
        actions.add(installButton);
        footer.add(actions, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }

    private JPanel versionBlock(String caption, JLabel value) {
        JPanel block = new JPanel(new BorderLayout(0, 5));
        block.setOpaque(false);
        JLabel label = new JLabel(caption, JLabel.CENTER);
        label.setForeground(MUTED_TEXT_COLOR);
        block.add(label, BorderLayout.NORTH);
        block.add(value, BorderLayout.CENTER);
        return block;
    }

    private void checkForUpdates() {
        checkButton.setEnabled(false);
        releaseButton.setEnabled(false);
        installButton.setEnabled(false);
        latestRelease = null;
        progressBar.setValue(0);
        progressBar.setString("等待下载");
        latestVersionValue.setText("检查中...");
        statusLabel.setForeground(MUTED_TEXT_COLOR);
        statusLabel.setText("正在连接 GitHub...");
        notesArea.setText("正在获取最新发布信息...");

        new SwingWorker<UpdateService.ReleaseInfo, Void>() {
            @Override
            protected UpdateService.ReleaseInfo doInBackground() throws Exception {
                return updateService.fetchLatestRelease();
            }

            @Override
            protected void done() {
                try {
                    showRelease(get());
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    showFailure("更新检查已中断");
                } catch (ExecutionException error) {
                    Throwable cause = error.getCause();
                    String message = cause == null || cause.getMessage() == null
                            ? "无法获取最新版本"
                            : cause.getMessage();
                    showFailure(message);
                } finally {
                    checkButton.setEnabled(true);
                }
            }
        }.execute();
    }

    private void showRelease(UpdateService.ReleaseInfo release) {
        latestRelease = release;
        releasePage = release.pageUri();
        latestVersionValue.setText(release.tagName());
        releaseButton.setEnabled(true);

        int comparison = UpdateService.compareVersions(release.version(), currentVersion);
        if (comparison > 0) {
            statusLabel.setForeground(ACCENT_COLOR);
            if (release.installer() == null) {
                statusLabel.setText("发现新版本，但没有可校验的 Windows 安装包");
                progressBar.setString("请打开发布页下载");
            } else if (!UpdateInstaller.isWindows()) {
                statusLabel.setText("发现新版本；自动安装仅支持 Windows");
                progressBar.setString("请打开发布页下载");
            } else {
                statusLabel.setText("发现新版本 " + release.tagName());
                installButton.setEnabled(true);
                progressBar.setString("可以下载更新");
            }
        } else if (comparison == 0) {
            statusLabel.setForeground(new Color(47, 120, 79));
            statusLabel.setText("当前已是最新版本");
            progressBar.setString("无需更新");
        } else {
            statusLabel.setForeground(MUTED_TEXT_COLOR);
            statusLabel.setText("当前版本比公开发布版本更新");
            progressBar.setString("无需更新");
        }

        String published = release.publishedAt() == null
                ? "发布时间未知"
                : "发布于 " + DATE_FORMAT.format(release.publishedAt());
        notesArea.setText(release.displayName() + "\n" + published + "\n\n" + release.notes());
        notesArea.setCaretPosition(0);
    }

    private void showFailure(String message) {
        latestRelease = null;
        releasePage = null;
        latestVersionValue.setText("获取失败");
        statusLabel.setForeground(new Color(158, 59, 72));
        statusLabel.setText("检查更新失败");
        notesArea.setText(message + "\n\n请确认网络连接后点击“重新检查”。");
        notesArea.setCaretPosition(0);
    }

    private void downloadAndInstall() {
        UpdateService.ReleaseInfo release = latestRelease;
        if (release == null || release.installer() == null) {
            return;
        }
        int answer = JOptionPane.showConfirmDialog(this,
                "将通过 ghfast.top 下载 " + release.installer().name()
                        + "。下载完成并通过 SHA-256 校验后，程序会启动安装器并退出。是否继续？",
                "下载并安装更新", JOptionPane.OK_CANCEL_OPTION, JOptionPane.INFORMATION_MESSAGE);
        if (answer != JOptionPane.OK_OPTION) {
            return;
        }

        setDownloadControlsEnabled(false);
        progressBar.setValue(0);
        progressBar.setString("准备下载...");
        statusLabel.setForeground(ACCENT_COLOR);
        statusLabel.setText("正在通过 ghfast.top 下载 " + release.tagName());

        new SwingWorker<Path, Integer>() {
            @Override
            protected Path doInBackground() throws Exception {
                return updateInstaller.download(release.installer(), value -> publish(value));
            }

            @Override
            protected void process(java.util.List<Integer> chunks) {
                int progress = chunks.get(chunks.size() - 1);
                progressBar.setValue(progress);
                progressBar.setString("已下载 " + progress + "%");
            }

            @Override
            protected void done() {
                try {
                    Path installer = get();
                    progressBar.setValue(100);
                    progressBar.setString("校验通过，正在启动安装器...");
                    statusLabel.setText("安装包已验证");
                    updateInstaller.launch(installer);
                    closeApplication();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    showDownloadFailure("下载已中断");
                } catch (ExecutionException error) {
                    Throwable cause = error.getCause();
                    String message = cause == null || cause.getMessage() == null
                            ? "下载或启动安装器失败"
                            : cause.getMessage();
                    showDownloadFailure(message);
                } catch (Exception error) {
                    showDownloadFailure(error.getMessage() == null ? "启动安装器失败" : error.getMessage());
                }
            }
        }.execute();
    }

    private void setDownloadControlsEnabled(boolean enabled) {
        checkButton.setEnabled(enabled);
        releaseButton.setEnabled(enabled && releasePage != null);
        installButton.setEnabled(enabled && latestRelease != null && latestRelease.installer() != null
                && UpdateInstaller.isWindows()
                && UpdateService.isUpdateAvailable(currentVersion, latestRelease.version()));
    }

    private void showDownloadFailure(String message) {
        statusLabel.setForeground(new Color(158, 59, 72));
        statusLabel.setText("更新安装失败");
        progressBar.setValue(0);
        progressBar.setString("下载失败");
        notesArea.setText(message + "\n\n安装程序没有启动，请稍后重试或打开发布页手动下载。");
        notesArea.setCaretPosition(0);
        setDownloadControlsEnabled(true);
    }

    private void closeApplication() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window != null) {
            window.dispatchEvent(new WindowEvent(window, WindowEvent.WINDOW_CLOSING));
        }
    }

    private void openReleasePage() {
        if (releasePage == null || !Desktop.isDesktopSupported()) {
            JOptionPane.showMessageDialog(this, "当前系统无法打开浏览器", "无法打开", JOptionPane.WARNING_MESSAGE);
            return;
        }
        try {
            Desktop.getDesktop().browse(releasePage);
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this, "打开发布页面失败：" + error.getMessage(),
                    "无法打开", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static JLabel versionLabel() {
        JLabel label = new JLabel("--", JLabel.CENTER);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 24f));
        label.setForeground(TEXT_COLOR);
        return label;
    }

    private static JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setForeground(TEXT_COLOR);
        button.setBackground(SURFACE_BACKGROUND);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CONTROL_BORDER),
                BorderFactory.createEmptyBorder(5, 11, 5, 11)));
        return button;
    }
}

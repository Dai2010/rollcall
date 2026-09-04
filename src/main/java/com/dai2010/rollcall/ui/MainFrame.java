package com.dai2010.rollcall.ui;

import com.dai2010.rollcall.AppVersion;
import com.dai2010.rollcall.data.NameListRepository;
import com.dai2010.rollcall.model.NameList;
import com.dai2010.rollcall.model.Person;
import com.dai2010.rollcall.service.DrawService;
import com.dai2010.rollcall.service.NameParser;
import com.dai2010.rollcall.service.UpdateInstaller;
import com.dai2010.rollcall.service.UpdateService;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.net.URI;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.awt.geom.RoundRectangle2D;

/** Main application window and its three functional tabs. */
public final class MainFrame extends javax.swing.JFrame {
    private static final Color APP_BACKGROUND = new Color(235, 245, 248);
    private static final Color SURFACE_BACKGROUND = new Color(255, 255, 255);
    private static final Color TEXT_COLOR = new Color(28, 55, 69);
    private static final Color MUTED_TEXT_COLOR = new Color(83, 111, 124);
    private static final Color CONTROL_BORDER = new Color(164, 193, 205);
    private static final Color SELECTION_BACKGROUND = new Color(193, 228, 241);
    private static final int WINDOW_RADIUS = 24;

    private final NameListRepository repository;
    private final NameListRepository.Snapshot snapshot;
    private final ListsPanel listsPanel;
    private final DrawPanel drawPanel;
    private final UpdatePanel updatePanel;
    private Rectangle restoredBounds;
    private boolean maximized;

    public MainFrame() {
        this(new NameListRepository());
    }

    public MainFrame(NameListRepository repository) {
        super("点名助手");
        this.repository = repository;
        NameListRepository.Snapshot loaded;
        try {
            loaded = repository.load();
        } catch (IOException | RuntimeException error) {
            loaded = new NameListRepository.Snapshot();
            SwingUtilities.invokeLater(() -> showError("读取名单文件失败：" + error.getMessage()));
        }
        this.snapshot = loaded;
        snapshot.sanitize();

        setUndecorated(true);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new java.awt.Dimension(780, 560));
        setSize(1000, 720);
        setBackground(APP_BACKGROUND);
        loadWindowIcon();
        setLocationRelativeTo(null);

        this.drawPanel = new DrawPanel(snapshot);
        this.listsPanel = new ListsPanel(snapshot, this::saveAndRefresh);
        this.updatePanel = new UpdatePanel(new UpdateService(), new UpdateInstaller(), AppVersion.current());
        JTabbedPane tabs = new JTabbedPane();
        tabs.setOpaque(false);
        tabs.setBackground(APP_BACKGROUND);
        tabs.setForeground(TEXT_COLOR);
        tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabs.addTab("抽人与分组", drawPanel);
        tabs.addTab("名单管理", listsPanel);
        tabs.addTab("检查更新", updatePanel);
        tabs.addTab("关于与帮助", new AboutPanel());
        RoundedPanel shell = new RoundedPanel(WINDOW_RADIUS, APP_BACKGROUND, new Color(164, 193, 205));
        shell.setLayout(new BorderLayout());
        shell.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        shell.add(createTitleBar(), BorderLayout.NORTH);
        shell.add(tabs, BorderLayout.CENTER);
        setContentPane(shell);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent event) {
                updateWindowShape();
            }
        });
        updateWindowShape();
    }

    public void startAutomaticUpdateCheck() {
        updatePanel.startAutomaticCheck();
    }

    private JPanel createTitleBar() {
        JPanel titleBar = new JPanel(new BorderLayout(8, 0));
        titleBar.setOpaque(false);
        titleBar.setBorder(BorderFactory.createEmptyBorder(7, 16, 3, 10));
        titleBar.setPreferredSize(new java.awt.Dimension(0, 44));

        javax.swing.JLabel title = new javax.swing.JLabel("点名助手");
        styleTitle(title);
        titleBar.add(title, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);
        JButton minimize = windowButton("—", "最小化");
        minimize.addActionListener(event -> setState(Frame.ICONIFIED));
        JButton maximize = windowButton("□", "最大化/还原");
        maximize.addActionListener(event -> toggleMaximize());
        JButton close = windowButton("×", "关闭");
        close.addActionListener(event -> dispatchEvent(new WindowEvent(this, WindowEvent.WINDOW_CLOSING)));
        close.setForeground(new Color(158, 59, 72));
        actions.add(minimize);
        actions.add(maximize);
        actions.add(close);
        titleBar.add(actions, BorderLayout.EAST);
        installWindowDrag(titleBar);
        return titleBar;
    }

    private static JButton windowButton(String text, String tooltip) {
        JButton button = button(text);
        button.setToolTipText(tooltip);
        button.setPreferredSize(new java.awt.Dimension(34, 28));
        button.setBackground(APP_BACKGROUND);
        button.setBorder(BorderFactory.createEmptyBorder());
        button.setFont(button.getFont().deriveFont(Font.BOLD, 15f));
        return button;
    }

    private void installWindowDrag(JPanel titleBar) {
        MouseAdapter dragHandler = new MouseAdapter() {
            private Point pressPoint;

            @Override
            public void mousePressed(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1 && !maximized) {
                    pressPoint = event.getPoint();
                }
            }

            @Override
            public void mouseDragged(MouseEvent event) {
                if (pressPoint == null || maximized) {
                    return;
                }
                Point location = getLocation();
                setLocation(location.x + event.getX() - pressPoint.x,
                        location.y + event.getY() - pressPoint.y);
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                pressPoint = null;
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() == MouseEvent.BUTTON1 && event.getClickCount() == 2) {
                    toggleMaximize();
                }
            }
        };
        titleBar.addMouseListener(dragHandler);
        titleBar.addMouseMotionListener(dragHandler);
    }

    private void toggleMaximize() {
        if (maximized) {
            if (restoredBounds != null) {
                setBounds(restoredBounds);
            }
            maximized = false;
            updateWindowShape();
            return;
        }
        restoredBounds = getBounds();
        Rectangle workArea = getGraphicsConfiguration().getBounds();
        java.awt.Insets insets = java.awt.Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
        workArea = new Rectangle(workArea.x + insets.left, workArea.y + insets.top,
                workArea.width - insets.left - insets.right, workArea.height - insets.top - insets.bottom);
        setBounds(workArea);
        maximized = true;
        updateWindowShape();
    }

    private void updateWindowShape() {
        if (getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        try {
            if (maximized) {
                setShape(null);
            } else {
                setShape(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), WINDOW_RADIUS, WINDOW_RADIUS));
            }
        } catch (UnsupportedOperationException | IllegalArgumentException ignored) {
            // Shaped windows are unavailable on a few legacy desktop configurations.
        }
    }

    private void loadWindowIcon() {
        try (InputStream input = MainFrame.class.getResourceAsStream("/icons/rollcall-icon.png")) {
            if (input != null) {
                setIconImage(ImageIO.read(input));
            }
        } catch (IOException ignored) {
            // The application remains usable when an optional icon resource is unavailable.
        }
    }

    private void saveAndRefresh() {
        try {
            snapshot.sanitize();
            repository.save(snapshot);
            drawPanel.refreshLists();
            listsPanel.refreshLists();
        } catch (IOException error) {
            showError("保存名单失败：" + error.getMessage());
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "操作失败", JOptionPane.ERROR_MESSAGE);
    }

    private static JButton button(String text) {
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

    private static void styleTitle(javax.swing.JLabel label) {
        label.setFont(label.getFont().deriveFont(Font.BOLD, 18f));
        label.setForeground(TEXT_COLOR);
    }

    private static void styleTextField(JTextField field) {
        field.setOpaque(true);
        field.setBackground(SURFACE_BACKGROUND);
        field.setForeground(TEXT_COLOR);
        field.setCaretColor(TEXT_COLOR);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(CONTROL_BORDER),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
    }

    private static void styleToggle(javax.swing.AbstractButton toggle) {
        toggle.setOpaque(false);
        toggle.setForeground(TEXT_COLOR);
    }

    private final class ListsPanel extends JPanel {
        private final NameListRepository.Snapshot data;
        private final Runnable onChanged;
        private final DefaultListModel<NameList> listModel = new DefaultListModel<>();
        private final JList<NameList> listBox = new JList<>(listModel);
        private final JPanel listArea = new JPanel(new CardLayout());
        private final JScrollPane listScroll = new JScrollPane(listBox);
        private final JLabel emptyListLabel = new JLabel(
                "<html><center>还没有名单<br>点击“新建”或“导入文件”开始</center></html>",
                JLabel.CENTER);
        private final JTextField remarkField = new JTextField();
        private final JCheckBox defaultCheck = new JCheckBox("设为默认抽取名单");
        private final DefaultListModel<Person> peopleModel = new DefaultListModel<>();
        private final JList<Person> peopleList = new JList<>(peopleModel);
        private final JPanel peopleArea = new JPanel(new CardLayout());
        private final JLabel emptyPeopleLabel = new JLabel("当前名单还没有成员", JLabel.CENTER);
        private final JTextField memberField = new JTextField();
        private final JLabel countLabel = new JLabel("共 0 人");

        private ListsPanel(NameListRepository.Snapshot data, Runnable onChanged) {
            super(new BorderLayout(12, 12));
            this.data = data;
            this.onChanged = onChanged;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
            build();
            refreshLists();
        }

        private void build() {
            JPanel left = new JPanel(new BorderLayout(8, 8));
            left.setOpaque(false);
            javax.swing.JLabel title = new javax.swing.JLabel("我的名单");
            styleTitle(title);
            left.add(title, BorderLayout.NORTH);
            listBox.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            listBox.setBackground(SURFACE_BACKGROUND);
            listBox.setForeground(TEXT_COLOR);
            listBox.setSelectionBackground(SELECTION_BACKGROUND);
            listBox.setSelectionForeground(TEXT_COLOR);
            listBox.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                         boolean selected, boolean focused) {
                    JList<?> target = list == null ? new JList<>() : list;
                    if (!(value instanceof NameList nameList)) {
                        return super.getListCellRendererComponent(target, "暂无名单，请先添加", index, selected, focused);
                    }
                    String suffix = nameList.getId().equals(data.getDefaultListId()) ? "  · 默认" : "";
                    return super.getListCellRendererComponent(target,
                            displayRemark(nameList) + " (" + nameList.getPeople().size() + ")" + suffix,
                            index, selected, focused);
                }
            });
            listBox.addListSelectionListener(event -> {
                if (!event.getValueIsAdjusting()) {
                    showSelectedList();
                }
            });
            listScroll.setBorder(BorderFactory.createLineBorder(CONTROL_BORDER));
            listScroll.setOpaque(false);
            listScroll.getViewport().setOpaque(false);
            listArea.setOpaque(false);
            emptyListLabel.setForeground(MUTED_TEXT_COLOR);
            listArea.add(listScroll, "list");
            listArea.add(emptyListLabel, "empty");
            left.add(listArea, BorderLayout.CENTER);
            JPanel listButtons = new JPanel(new GridLayout(1, 3, 6, 0));
            listButtons.setOpaque(false);
            JButton addButton = button("新建");
            addButton.addActionListener(event -> createList());
            JButton importButton = button("导入文件");
            importButton.addActionListener(event -> importFiles());
            JButton deleteButton = button("删除");
            deleteButton.addActionListener(event -> deleteSelectedList());
            listButtons.add(addButton);
            listButtons.add(importButton);
            listButtons.add(deleteButton);
            left.add(listButtons, BorderLayout.SOUTH);
            left.setPreferredSize(new java.awt.Dimension(250, 0));

            JPanel details = new JPanel(new BorderLayout(8, 8));
            details.setOpaque(false);
            JPanel heading = new JPanel(new BorderLayout(8, 0));
            heading.setOpaque(false);
            javax.swing.JLabel detailsTitle = new javax.swing.JLabel("名单详情");
            styleTitle(detailsTitle);
            heading.add(detailsTitle, BorderLayout.WEST);
            styleToggle(defaultCheck);
            defaultCheck.addActionListener(event -> {
                NameList selected = selectedList();
                if (selected != null && defaultCheck.isSelected()) {
                    data.setDefaultListId(selected.getId());
                    onChanged.run();
                }
            });
            defaultCheck.setEnabled(false);
            heading.add(defaultCheck, BorderLayout.EAST);
            details.add(heading, BorderLayout.NORTH);

            JPanel editor = new JPanel(new BorderLayout(6, 0));
            editor.setOpaque(false);
            styleTextField(remarkField);
            editor.add(new javax.swing.JLabel("备注："), BorderLayout.WEST);
            editor.add(remarkField, BorderLayout.CENTER);
            JButton saveRemark = button("保存备注");
            saveRemark.addActionListener(event -> saveRemark());
            editor.add(saveRemark, BorderLayout.EAST);

            JPanel memberHeader = new JPanel(new BorderLayout());
            memberHeader.setOpaque(false);
            memberHeader.add(editor, BorderLayout.CENTER);
            countLabel.setForeground(MUTED_TEXT_COLOR);
            memberHeader.add(countLabel, BorderLayout.SOUTH);
            peopleList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            peopleList.setBackground(SURFACE_BACKGROUND);
            peopleList.setForeground(TEXT_COLOR);
            peopleList.setSelectionBackground(SELECTION_BACKGROUND);
            peopleList.setSelectionForeground(TEXT_COLOR);
            peopleList.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                         boolean selected, boolean focused) {
                    if (!(value instanceof Person person)) {
                        return new JLabel();
                    }
                    NameChip chip = new NameChip(person.getId() + "  " + person.getName());
                    chip.setOpaque(false);
                    chip.setBackground(selected ? new Color(130, 190, 220, 100) : new Color(0, 0, 0, 0));
                    return chip;
                }
            });
            JScrollPane peopleScroll = new JScrollPane(peopleList);
            peopleScroll.setBorder(BorderFactory.createLineBorder(CONTROL_BORDER));
            peopleScroll.setOpaque(false);
            peopleScroll.getViewport().setOpaque(false);
            peopleArea.setOpaque(false);
            emptyPeopleLabel.setForeground(MUTED_TEXT_COLOR);
            peopleArea.add(peopleScroll, "list");
            peopleArea.add(emptyPeopleLabel, "empty");
            RoundedPanel peopleSurface = new RoundedPanel(16, new Color(247, 251, 253), new Color(170, 198, 211));
            peopleSurface.setLayout(new BorderLayout());
            peopleSurface.add(peopleArea);
            peopleSurface.setPreferredSize(new java.awt.Dimension(0, 280));

            JPanel memberCenter = new JPanel(new BorderLayout(8, 8));
            memberCenter.setOpaque(false);
            memberCenter.add(memberHeader, BorderLayout.NORTH);
            memberCenter.add(peopleSurface, BorderLayout.CENTER);
            details.add(memberCenter, BorderLayout.CENTER);

            JPanel memberActions = new JPanel(new BorderLayout(6, 0));
            memberActions.setOpaque(false);
            styleTextField(memberField);
            memberActions.add(memberField, BorderLayout.CENTER);
            memberField.getDocument().addDocumentListener(new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) { }
                @Override public void removeUpdate(DocumentEvent event) { }
                @Override public void changedUpdate(DocumentEvent event) { }
            });
            JButton addMember = button("添加成员");
            addMember.addActionListener(event -> addMembers());
            JButton removeMember = button("移除选中");
            removeMember.addActionListener(event -> removeMembers());
            JPanel actionButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            actionButtons.setOpaque(false);
            actionButtons.add(addMember);
            actionButtons.add(removeMember);
            memberActions.add(actionButtons, BorderLayout.EAST);
            details.add(memberActions, BorderLayout.SOUTH);

            add(left, BorderLayout.WEST);
            add(details, BorderLayout.CENTER);
        }

        private void refreshLists() {
            String selectedId = selectedList() == null ? null : selectedList().getId();
            listModel.clear();
            for (NameList nameList : data.getLists()) {
                listModel.addElement(nameList);
            }
            showListArea();
            if (selectedId != null) {
                selectById(selectedId);
            }
            if (listBox.getSelectedIndex() < 0 && !listModel.isEmpty()) {
                listBox.setSelectedIndex(0);
            }
            showSelectedList();
        }

        private void showSelectedList() {
            NameList selected = selectedList();
            boolean hasSelection = selected != null;
            remarkField.setEnabled(hasSelection);
            defaultCheck.setEnabled(hasSelection);
            if (!hasSelection) {
                remarkField.setText("");
                defaultCheck.setSelected(false);
                peopleModel.clear();
                countLabel.setText("共 0 人");
                showPeopleArea(false);
                return;
            }
            remarkField.setText(displayRemark(selected));
            defaultCheck.setSelected(selected.getId().equals(data.getDefaultListId()));
            peopleModel.clear();
            for (Person person : selected.getPeople()) {
                peopleModel.addElement(person);
            }
            countLabel.setText("共 " + selected.getPeople().size() + " 人");
            showPeopleArea(!peopleModel.isEmpty());
        }

        private void showListArea() {
            CardLayout layout = (CardLayout) listArea.getLayout();
            layout.show(listArea, listModel.isEmpty() ? "empty" : "list");
        }

        private void showPeopleArea(boolean hasPeople) {
            CardLayout layout = (CardLayout) peopleArea.getLayout();
            layout.show(peopleArea, hasPeople ? "list" : "empty");
        }

        private NameList selectedList() {
            return listBox.getSelectedValue();
        }

        private void selectById(String id) {
            for (int index = 0; index < listModel.size(); index++) {
                if (listModel.get(index).getId().equals(id)) {
                    listBox.setSelectedIndex(index);
                    return;
                }
            }
        }

        private void createList() {
            String remark = JOptionPane.showInputDialog(this, "请输入名单备注（留空将自动命名）", "新建名单",
                    JOptionPane.QUESTION_MESSAGE);
            if (remark == null) {
                return;
            }
            String finalRemark = remark.isBlank() ? nextListName() : remark.trim();
            NameList nameList = new NameList(finalRemark, List.of());
            data.getLists().add(nameList);
            if (data.getDefaultListId() == null) {
                data.setDefaultListId(nameList.getId());
            }
            onChanged.run();
            selectById(nameList.getId());
        }

        private void importFiles() {
            JFileChooser chooser = new JFileChooser();
            chooser.setMultiSelectionEnabled(true);
            if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            NameParser parser = new NameParser();
            for (java.io.File file : chooser.getSelectedFiles()) {
                Path path = file.toPath();
                try {
                    List<Person> people = parser.parseFile(path);
                    if (people.isEmpty()) {
                        showError("文件中没有识别到姓名：" + path.getFileName());
                        continue;
                    }
                    String suggested = stripExtension(path.getFileName().toString());
                    String remark = JOptionPane.showInputDialog(this,
                            "请输入“" + path.getFileName() + "”的名单备注（留空将自动命名）", suggested);
                    if (remark == null) {
                        continue;
                    }
                    String finalRemark = remark.isBlank() ? nextListName() : remark.trim();
                    NameList nameList = new NameList(finalRemark, people);
                    data.getLists().add(nameList);
                    if (data.getDefaultListId() == null) {
                        data.setDefaultListId(nameList.getId());
                    }
                    onChanged.run();
                    selectById(nameList.getId());
                } catch (IOException | RuntimeException error) {
                    showError("导入“" + path.getFileName() + "”失败：" + error.getMessage());
                }
            }
        }

        private void deleteSelectedList() {
            NameList selected = selectedList();
            if (selected == null) {
                return;
            }
            int choice = JOptionPane.showConfirmDialog(this, "确定删除名单“" + displayRemark(selected) + "”吗？",
                    "确认删除", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
            data.getLists().remove(selected);
            if (selected.getId().equals(data.getDefaultListId())) {
                data.setDefaultListId(data.getLists().isEmpty() ? null : data.getLists().get(0).getId());
            }
            onChanged.run();
        }

        private void saveRemark() {
            NameList selected = selectedList();
            if (selected == null) {
                return;
            }
            String value = remarkField.getText().trim();
            selected.setRemark(value.isBlank() ? nextListName() : value);
            onChanged.run();
        }

        private void addMembers() {
            NameList selected = selectedList();
            if (selected == null) {
                showError("请先选择一个名单");
                return;
            }
            List<Person> parsed = new NameParser().parseManual(memberField.getText());
            if (parsed.isEmpty()) {
                showError("没有识别到姓名，请使用引号、分号或其他分隔符分开");
                return;
            }
            Set<String> existing = new LinkedHashSet<>();
            for (Person person : selected.getPeople()) {
                existing.add(person.getName());
            }
            for (Person person : parsed) {
                if (existing.add(person.getName())) {
                    selected.addPerson(person.getName());
                }
            }
            selected.normalizeIds();
            memberField.setText("");
            onChanged.run();
        }

        private void removeMembers() {
            NameList selected = selectedList();
            if (selected == null || peopleList.getSelectedIndices().length == 0) {
                return;
            }
            Set<Person> remove = new LinkedHashSet<>(peopleList.getSelectedValuesList());
            selected.getPeople().removeIf(remove::contains);
            selected.normalizeIds();
            onChanged.run();
        }

        private String nextListName() {
            int number = data.getLists().size() + 1;
            Set<String> existing = new LinkedHashSet<>();
            data.getLists().forEach(list -> existing.add(list.getRemark()));
            while (existing.contains("名单" + number)) {
                number++;
            }
            return "名单" + number;
        }

        private String displayRemark(NameList nameList) {
            return nameList.getRemark() == null || nameList.getRemark().isBlank() ? "未命名名单" : nameList.getRemark();
        }

        private String stripExtension(String name) {
            int dot = name.lastIndexOf('.');
            return dot > 0 ? name.substring(0, dot) : name;
        }
    }

    private static final class DrawPanel extends JPanel {
        private final NameListRepository.Snapshot data;
        private final DrawService drawService = new DrawService();
        private final Set<Integer> drawnIds = new LinkedHashSet<>();
        private final JComboBox<NameList> listCombo = new JComboBox<>();
        private final JRadioButton singleMode = new JRadioButton("单人", true);
        private final JRadioButton multipleMode = new JRadioButton("多人");
        private final JCheckBox continuousCheck = new JCheckBox("连续抽人（不重复）");
        private final JSpinner perGroupSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1_000_000, 1));
        private final JSpinner groupSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1_000_000, 1));
        private final JLabel perGroupLabel = new JLabel("每组人数");
        private final JLabel groupLabel = new JLabel("组数");
        private final JLabel counterLabel = new JLabel("已抽 0 人 · 未抽 0 人 · 总人数 0");
        private final JPanel resultPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        private final JPanel resultArea = new JPanel(new CardLayout());
        private final JScrollPane resultScroll = new JScrollPane(resultPanel);
        private final JLabel emptyResultLabel = new JLabel("抽取结果会显示在这里", JLabel.CENTER);
        private final RoundedPanel resultSurface = new RoundedPanel(16, new Color(247, 251, 253), new Color(170, 198, 211));

        private DrawPanel(NameListRepository.Snapshot data) {
            super(new BorderLayout(12, 12));
            this.data = data;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
            build();
            refreshLists();
        }

        private void build() {
            RoundedPanel top = new RoundedPanel(16, new Color(255, 255, 255), new Color(170, 198, 211));
            top.setLayout(new GridBagLayout());
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(4, 5, 4, 5);
            constraints.anchor = GridBagConstraints.WEST;
            constraints.fill = GridBagConstraints.HORIZONTAL;
            constraints.gridy = 0;
            constraints.gridx = 0;
            top.add(new javax.swing.JLabel("抽取名单"), constraints);
            constraints.gridx++;
            constraints.weightx = 1;
            listCombo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                         boolean selected, boolean focused) {
                    JList<?> target = list == null ? new JList<>() : list;
                    if (!(value instanceof NameList nameList)) {
                        return super.getListCellRendererComponent(target, "暂无名单，请先添加", index, selected, focused);
                    }
                    return super.getListCellRendererComponent(target,
                            nameList.getRemark() + " (" + nameList.getPeople().size() + " 人)", index, selected, focused);
                }
            });
            listCombo.setOpaque(true);
            listCombo.setBackground(SURFACE_BACKGROUND);
            listCombo.setForeground(TEXT_COLOR);
            listCombo.addActionListener(event -> {
                drawnIds.clear();
                clearResults();
                updateCounter();
            });
            top.add(listCombo, constraints);
            constraints.gridx++;
            constraints.weightx = 0;
            top.add(new javax.swing.JLabel("模式"), constraints);
            constraints.gridx++;
            JPanel modes = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            modes.setOpaque(false);
            ButtonGroup modeGroup = new ButtonGroup();
            modeGroup.add(singleMode);
            modeGroup.add(multipleMode);
            modes.add(singleMode);
            modes.add(multipleMode);
            top.add(modes, constraints);
            constraints.gridx++;
            top.add(continuousCheck, constraints);

            styleToggle(singleMode);
            styleToggle(multipleMode);
            styleToggle(continuousCheck);
            perGroupLabel.setForeground(TEXT_COLOR);
            groupLabel.setForeground(TEXT_COLOR);

            constraints.gridy = 1;
            constraints.gridx = 0;
            top.add(perGroupLabel, constraints);
            constraints.gridx++;
            constraints.weightx = 0.2;
            top.add(perGroupSpinner, constraints);
            constraints.gridx++;
            top.add(groupLabel, constraints);
            constraints.gridx++;
            top.add(groupSpinner, constraints);
            constraints.gridx++;
            constraints.weightx = 0;
            JButton drawButton = button("开始抽取");
            drawButton.setBackground(new Color(66, 145, 178));
            drawButton.setForeground(Color.WHITE);
            drawButton.addActionListener(this::draw);
            top.add(drawButton, constraints);
            JButton resetButton = button("重置连续状态");
            resetButton.addActionListener(event -> {
                drawnIds.clear();
                clearResults();
                updateCounter();
            });
            constraints.gridx++;
            top.add(resetButton, constraints);

            singleMode.addActionListener(event -> updateModeControls());
            multipleMode.addActionListener(event -> updateModeControls());
            continuousCheck.addActionListener(event -> {
                if (!continuousCheck.isSelected()) {
                    drawnIds.clear();
                }
                updateCounter();
            });
            add(top, BorderLayout.NORTH);

            resultSurface.setLayout(new BorderLayout());
            resultPanel.setOpaque(false);
            resultScroll.setBorder(BorderFactory.createEmptyBorder());
            resultScroll.setOpaque(false);
            resultScroll.getViewport().setOpaque(false);
            resultArea.setOpaque(false);
            emptyResultLabel.setForeground(MUTED_TEXT_COLOR);
            resultArea.add(resultScroll, "results");
            resultArea.add(emptyResultLabel, "empty");
            resultSurface.add(resultArea, BorderLayout.CENTER);
            add(resultSurface, BorderLayout.CENTER);
            JPanel bottom = new JPanel(new BorderLayout());
            bottom.setOpaque(false);
            counterLabel.setForeground(TEXT_COLOR);
            bottom.add(counterLabel, BorderLayout.WEST);
            javax.swing.JLabel hint = new javax.swing.JLabel("连续模式关闭时，每次抽取可重复。");
            hint.setForeground(MUTED_TEXT_COLOR);
            bottom.add(hint, BorderLayout.EAST);
            add(bottom, BorderLayout.SOUTH);
            clearResults();
            updateModeControls();
        }

        private void refreshLists() {
            String selectedId = selectedList() == null ? data.getDefaultListId() : selectedList().getId();
            DefaultComboBoxModel<NameList> model = new DefaultComboBoxModel<>();
            for (NameList nameList : data.getLists()) {
                model.addElement(nameList);
            }
            listCombo.setModel(model);
            if (selectedId != null) {
                for (int index = 0; index < model.getSize(); index++) {
                    if (model.getElementAt(index).getId().equals(selectedId)) {
                        listCombo.setSelectedIndex(index);
                        break;
                    }
                }
            }
            updateModeControls();
            updateCounter();
        }

        private void updateModeControls() {
            boolean multiple = multipleMode.isSelected();
            perGroupLabel.setVisible(multiple);
            perGroupSpinner.setVisible(multiple);
            groupLabel.setVisible(multiple);
            groupSpinner.setVisible(multiple);
            revalidate();
            repaint();
        }

        private void draw(ActionEvent event) {
            NameList nameList = selectedList();
            if (nameList == null) {
                showMessage("请先在“名单管理”中添加并选择名单", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (nameList.getPeople().isEmpty()) {
                showMessage("当前名单没有成员，请先添加姓名", JOptionPane.WARNING_MESSAGE);
                return;
            }
            boolean continuous = continuousCheck.isSelected();
            try {
                if (singleMode.isSelected()) {
                    DrawService.DrawResult result = drawService.draw(nameList.getPeople(), 1, continuous, drawnIds);
                    renderPeople(List.of(result.selected()));
                } else {
                    int perGroup = ((Number) perGroupSpinner.getValue()).intValue();
                    int groups = ((Number) groupSpinner.getValue()).intValue();
                    List<List<Person>> results = drawService.drawGroups(nameList.getPeople(), perGroup, groups,
                            continuous, drawnIds);
                    renderGroups(results);
                }
                updateCounter();
            } catch (DrawService.InsufficientPeopleException error) {
                showMessage(error.getMessage(), JOptionPane.WARNING_MESSAGE);
            } catch (IllegalArgumentException error) {
                showMessage(error.getMessage(), JOptionPane.WARNING_MESSAGE);
            }
        }

        private void renderPeople(List<List<Person>> groups) {
            resultPanel.removeAll();
            for (List<Person> group : groups) {
                for (Person person : group) {
                    resultPanel.add(new NameChip(person.getName()));
                }
            }
            showResultArea(resultPanel.getComponentCount() > 0);
            resultPanel.revalidate();
            resultPanel.repaint();
        }

        private void renderGroups(List<List<Person>> groups) {
            resultPanel.removeAll();
            for (int index = 0; index < groups.size(); index++) {
                JPanel groupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
                groupPanel.setOpaque(false);
                groupPanel.add(new javax.swing.JLabel("第 " + (index + 1) + " 组"));
                for (Person person : groups.get(index)) {
                    groupPanel.add(new NameChip(person.getName()));
                }
                resultPanel.add(groupPanel);
            }
            showResultArea(resultPanel.getComponentCount() > 0);
            resultPanel.revalidate();
            resultPanel.repaint();
        }

        private NameList selectedList() {
            return (NameList) listCombo.getSelectedItem();
        }

        private void clearResults() {
            resultPanel.removeAll();
            showResultArea(false);
            resultPanel.revalidate();
            resultPanel.repaint();
        }

        private void showResultArea(boolean hasResults) {
            CardLayout layout = (CardLayout) resultArea.getLayout();
            layout.show(resultArea, hasResults ? "results" : "empty");
        }

        private void updateCounter() {
            NameList nameList = selectedList();
            int total = nameList == null ? 0 : nameList.getPeople().size();
            int drawn = continuousCheck.isSelected() ? drawnIds.size() : 0;
            counterLabel.setText("已抽 " + drawn + " 人 · 未抽 " + (total - drawn) + " 人 · 总人数 " + total);
        }

        private void showMessage(String message, int type) {
            JOptionPane.showMessageDialog(this, message, "提示", type);
        }
    }

    private static final class AboutPanel extends JPanel {
        private AboutPanel() {
            super(new BorderLayout(14, 14));
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
            javax.swing.JLabel title = new javax.swing.JLabel("点名助手");
            styleTitle(title);
            add(title, BorderLayout.NORTH);

            JTextArea help = new JTextArea();
            help.setEditable(false);
            help.setLineWrap(true);
            help.setWrapStyleWord(true);
            help.setFont(UIManager.getFont("Label.font"));
            help.setForeground(TEXT_COLOR);
            help.setBackground(SURFACE_BACKGROUND);
            help.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
            help.setText("使用说明\n\n"
                    + "1. 在“名单管理”中新建名单或一次选择多个文件导入。导入时可以填写备注；留空会自动使用“名单一、名单二……”命名。\n"
                    + "2. 手动添加姓名时，可以使用全角/半角引号和分号等分隔符，例如：\"张三\"\"李四\"、“张三”；“李四”。\n"
                    + "3. 名单文件支持 UTF-8 文本和 JSON。文本可以每行一个姓名，也可以使用“编号,姓名”或“姓名,编号”；缺少编号时程序会自动补齐。\n"
                    + "4. 在“抽人与分组”选择名单。连续抽人开启后会记录已抽成员，关闭开关会立即重置记录。多人模式可设置每组人数和组数，组数留空时按一组处理。\n"
                    + "5. 连续模式下，如果剩余人数不足以完成下一组，程序会提示并保留当前状态。抽取人数超过名单总数时会被拒绝。\n\n"
                    + "名单格式示例\n"
                    + "纯文本：\n1,张三\n2,李四\n3,王五\n\n"
                    + "JSON：\n[{\"id\":1,\"name\":\"张三\"},{\"name\":\"李四\"}]\n\n"
                    + "本项目以 GNU GPL v3.0 协议发布。名单数据保存在用户目录下的 .rollcall/lists.json。\n");
            JScrollPane helpScroll = new JScrollPane(help);
            helpScroll.setBorder(BorderFactory.createLineBorder(CONTROL_BORDER));
            helpScroll.setOpaque(false);
            helpScroll.getViewport().setOpaque(false);
            add(helpScroll, BorderLayout.CENTER);

            JPanel links = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            links.setOpaque(false);
            links.add(new javax.swing.JLabel("作者：Dai2010"));
            JButton author = button("作者 GitHub");
            author.addActionListener(event -> openUrl("https://github.com/Dai2010"));
            JButton source = button("项目源代码");
            source.addActionListener(event -> openUrl("https://github.com/Dai2010/rollcall"));
            links.add(author);
            links.add(source);
            add(links, BorderLayout.SOUTH);
        }

        private void openUrl(String url) {
            if (!Desktop.isDesktopSupported()) {
                return;
            }
            try {
                Desktop.getDesktop().browse(URI.create(url));
            } catch (Exception ignored) {
                // The link remains visible when the desktop browser is unavailable.
            }
        }
    }
}

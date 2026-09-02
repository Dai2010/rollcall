package com.dai2010.rollcall.ui;

import com.dai2010.rollcall.data.NameListRepository;
import com.dai2010.rollcall.model.NameList;
import com.dai2010.rollcall.model.Person;
import com.dai2010.rollcall.service.DrawService;
import com.dai2010.rollcall.service.NameParser;

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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Main application window and its three functional tabs. */
public final class MainFrame extends javax.swing.JFrame {
    private final NameListRepository repository;
    private final NameListRepository.Snapshot snapshot;
    private final ListsPanel listsPanel;
    private final DrawPanel drawPanel;

    public MainFrame() {
        this(new NameListRepository());
    }

    public MainFrame(NameListRepository repository) {
        super("点名助手");
        this.repository = repository;
        NameListRepository.Snapshot loaded;
        try {
            loaded = repository.load();
        } catch (IOException error) {
            loaded = new NameListRepository.Snapshot();
            SwingUtilities.invokeLater(() -> showError("读取名单文件失败：" + error.getMessage()));
        }
        this.snapshot = loaded;
        snapshot.sanitize();

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new java.awt.Dimension(780, 560));
        setSize(1000, 720);
        loadWindowIcon();
        setLocationRelativeTo(null);

        this.drawPanel = new DrawPanel(snapshot);
        this.listsPanel = new ListsPanel(snapshot, this::saveAndRefresh);
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("抽人与分组", drawPanel);
        tabs.addTab("名单管理", listsPanel);
        tabs.addTab("关于与帮助", new AboutPanel());
        add(tabs);
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
        return button;
    }

    private static void styleTitle(javax.swing.JLabel label) {
        label.setFont(label.getFont().deriveFont(Font.BOLD, 18f));
        label.setForeground(new Color(35, 69, 88));
    }

    private final class ListsPanel extends JPanel {
        private final NameListRepository.Snapshot data;
        private final Runnable onChanged;
        private final DefaultListModel<NameList> listModel = new DefaultListModel<>();
        private final JList<NameList> listBox = new JList<>(listModel);
        private final JTextField remarkField = new JTextField();
        private final JCheckBox defaultCheck = new JCheckBox("设为默认抽取名单");
        private final DefaultListModel<Person> peopleModel = new DefaultListModel<>();
        private final JList<Person> peopleList = new JList<>(peopleModel);
        private final JTextField memberField = new JTextField();
        private final JLabel countLabel = new JLabel("共 0 人");

        private ListsPanel(NameListRepository.Snapshot data, Runnable onChanged) {
            super(new BorderLayout(12, 12));
            this.data = data;
            this.onChanged = onChanged;
            setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
            build();
            refreshLists();
        }

        private void build() {
            JPanel left = new JPanel(new BorderLayout(8, 8));
            javax.swing.JLabel title = new javax.swing.JLabel("我的名单");
            styleTitle(title);
            left.add(title, BorderLayout.NORTH);
            listBox.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            listBox.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                         boolean selected, boolean focused) {
                    NameList nameList = (NameList) value;
                    String suffix = nameList.getId().equals(data.getDefaultListId()) ? "  · 默认" : "";
                    return super.getListCellRendererComponent(list,
                            displayRemark(nameList) + " (" + nameList.getPeople().size() + ")" + suffix,
                            index, selected, focused);
                }
            });
            listBox.addListSelectionListener(event -> {
                if (!event.getValueIsAdjusting()) {
                    showSelectedList();
                }
            });
            left.add(new JScrollPane(listBox), BorderLayout.CENTER);
            JPanel listButtons = new JPanel(new GridLayout(1, 3, 6, 0));
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
            JPanel heading = new JPanel(new BorderLayout(8, 0));
            heading.add(new javax.swing.JLabel("名单详情"), BorderLayout.WEST);
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
            editor.add(new javax.swing.JLabel("备注："), BorderLayout.WEST);
            editor.add(remarkField, BorderLayout.CENTER);
            JButton saveRemark = button("保存备注");
            saveRemark.addActionListener(event -> saveRemark());
            editor.add(saveRemark, BorderLayout.EAST);

            JPanel memberHeader = new JPanel(new BorderLayout());
            memberHeader.add(editor, BorderLayout.CENTER);
            memberHeader.add(countLabel, BorderLayout.SOUTH);
            peopleList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            peopleList.setCellRenderer(new DefaultListCellRenderer() {
                @Override
                public java.awt.Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                         boolean selected, boolean focused) {
                    Person person = (Person) value;
                    NameChip chip = new NameChip(person.getId() + "  " + person.getName());
                    chip.setOpaque(false);
                    chip.setBackground(selected ? new Color(130, 190, 220, 100) : new Color(0, 0, 0, 0));
                    return chip;
                }
            });
            JScrollPane peopleScroll = new JScrollPane(peopleList);
            peopleScroll.setBorder(BorderFactory.createEmptyBorder());
            RoundedPanel peopleSurface = new RoundedPanel(16, new Color(247, 251, 253, 235), new Color(170, 198, 211, 180));
            peopleSurface.setLayout(new BorderLayout());
            peopleSurface.add(peopleScroll);
            peopleSurface.setPreferredSize(new java.awt.Dimension(0, 280));

            JPanel memberCenter = new JPanel(new BorderLayout(8, 8));
            memberCenter.add(memberHeader, BorderLayout.NORTH);
            memberCenter.add(peopleSurface, BorderLayout.CENTER);
            details.add(memberCenter, BorderLayout.CENTER);

            JPanel memberActions = new JPanel(new BorderLayout(6, 0));
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
                return;
            }
            remarkField.setText(displayRemark(selected));
            defaultCheck.setSelected(selected.getId().equals(data.getDefaultListId()));
            peopleModel.clear();
            for (Person person : selected.getPeople()) {
                peopleModel.addElement(person);
            }
            countLabel.setText("共 " + selected.getPeople().size() + " 人");
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
        private final RoundedPanel resultSurface = new RoundedPanel(16, new Color(247, 251, 253, 235), new Color(170, 198, 211, 180));

        private DrawPanel(NameListRepository.Snapshot data) {
            super(new BorderLayout(12, 12));
            this.data = data;
            setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
            build();
            refreshLists();
        }

        private void build() {
            JPanel top = new JPanel(new GridBagLayout());
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
                    NameList nameList = (NameList) value;
                    return super.getListCellRendererComponent(list,
                            nameList.getRemark() + " (" + nameList.getPeople().size() + " 人)", index, selected, focused);
                }
            });
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
            ButtonGroup modeGroup = new ButtonGroup();
            modeGroup.add(singleMode);
            modeGroup.add(multipleMode);
            modes.add(singleMode);
            modes.add(multipleMode);
            top.add(modes, constraints);
            constraints.gridx++;
            top.add(continuousCheck, constraints);

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
            resultSurface.add(new JScrollPane(resultPanel), BorderLayout.CENTER);
            add(resultSurface, BorderLayout.CENTER);
            JPanel bottom = new JPanel(new BorderLayout());
            bottom.add(counterLabel, BorderLayout.WEST);
            bottom.add(new javax.swing.JLabel("连续模式关闭时，每次抽取可重复。"), BorderLayout.EAST);
            add(bottom, BorderLayout.SOUTH);
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
            resultPanel.revalidate();
            resultPanel.repaint();
        }

        private NameList selectedList() {
            return (NameList) listCombo.getSelectedItem();
        }

        private void clearResults() {
            resultPanel.removeAll();
            resultPanel.revalidate();
            resultPanel.repaint();
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
            setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));
            javax.swing.JLabel title = new javax.swing.JLabel("点名助手");
            styleTitle(title);
            add(title, BorderLayout.NORTH);

            JTextArea help = new JTextArea();
            help.setEditable(false);
            help.setLineWrap(true);
            help.setWrapStyleWord(true);
            help.setFont(UIManager.getFont("Label.font"));
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
            add(new JScrollPane(help), BorderLayout.CENTER);

            JPanel links = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
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

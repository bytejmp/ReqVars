package com.reqvars.ui;

import com.reqvars.model.Profile;
import com.reqvars.model.Variable;
import com.reqvars.service.CurlParser;
import com.reqvars.service.PlaceholderService;
import com.reqvars.storage.ConfigManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ReqVarsTab extends JPanel {

    private static final String DEFAULT_REQUEST =
            "GET /api/v1/me HTTP/1.1\n" +
            "Host: target.com\n" +
            "\n";

    private static final String[] TOOL_NAMES = {"REPEATER", "INTRUDER", "SCANNER", "PROXY", "EXTENSIONS"};

    private final ConfigManager configManager;
    private final PlaceholderService placeholderService;
    private final JCheckBox enabledCheckbox;
    private final JCheckBox[] toolCheckboxes;
    private final JTabbedPane tabbedPane;
    private final JTextArea requestArea;
    private final JTextArea responseArea;

    private VariableTableModel currentTableModel;
    private JTable currentTable;
    private boolean updatingTabs = false;
    private boolean tabListenerInstalled = false;
    private final Timer previewDebounce;

    public ReqVarsTab(ConfigManager configManager, PlaceholderService placeholderService) {
        this.configManager = configManager;
        this.placeholderService = placeholderService;
        this.enabledCheckbox = new JCheckBox("Enable substitution", configManager.isSubstitutionEnabled());
        this.toolCheckboxes = new JCheckBox[TOOL_NAMES.length];
        this.tabbedPane = new JTabbedPane(JTabbedPane.TOP);
        this.requestArea = new JTextArea(DEFAULT_REQUEST);
        this.responseArea = new JTextArea();
        this.previewDebounce = new Timer(150, e -> doUpdatePreview());
        this.previewDebounce.setRepeats(false);

        setLayout(new BorderLayout());

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                createTopPanel(), createPreviewPanel());
        mainSplit.setResizeWeight(0.6);
        mainSplit.setDividerLocation(350);

        add(mainSplit, BorderLayout.CENTER);

        rebuildTabs();
        updatePreview();
    }

    public void refresh() {
        rebuildTabs();
        updatePreview();
    }

    // --- Top section: settings bar + tabbed profiles ---

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(6, 8, 4, 8));

        panel.add(createSettingsBar(), BorderLayout.NORTH);
        panel.add(tabbedPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createSettingsBar() {
        JPanel panel = new JPanel(new BorderLayout());

        // Left: title + scope
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        JLabel title = new JLabel("ReqVars");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        leftPanel.add(title);

        leftPanel.add(Box.createHorizontalStrut(15));
        leftPanel.add(new JLabel("Scope:"));
        Set<String> enabledTools = configManager.getEnabledTools();
        for (int i = 0; i < TOOL_NAMES.length; i++) {
            toolCheckboxes[i] = new JCheckBox(
                    TOOL_NAMES[i].substring(0, 1) + TOOL_NAMES[i].substring(1).toLowerCase(),
                    enabledTools.contains(TOOL_NAMES[i]));
            toolCheckboxes[i].setFont(toolCheckboxes[i].getFont().deriveFont(11f));
            toolCheckboxes[i].addActionListener(e -> onToolScopeChanged());
            leftPanel.add(toolCheckboxes[i]);
        }

        panel.add(leftPanel, BorderLayout.WEST);

        // Right: enable + tab management
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        enabledCheckbox.addActionListener(e -> {
            configManager.setSubstitutionEnabled(enabledCheckbox.isSelected());
            updatePreview();
        });
        rightPanel.add(enabledCheckbox);

        rightPanel.add(Box.createHorizontalStrut(10));

        JButton addTabBtn = new JButton("+ Tab");
        addTabBtn.setMargin(new Insets(1, 6, 1, 6));
        addTabBtn.setToolTipText("New tab (identity/role)");
        addTabBtn.addActionListener(this::onNewTab);
        rightPanel.add(addTabBtn);

        JButton dupTabBtn = new JButton("Dup");
        dupTabBtn.setMargin(new Insets(1, 6, 1, 6));
        dupTabBtn.setToolTipText("Duplicate current tab");
        dupTabBtn.addActionListener(this::onDuplicateTab);
        rightPanel.add(dupTabBtn);

        JButton renTabBtn = new JButton("Rename");
        renTabBtn.setMargin(new Insets(1, 6, 1, 6));
        renTabBtn.addActionListener(this::onRenameTab);
        rightPanel.add(renTabBtn);

        JButton delTabBtn = new JButton("X");
        delTabBtn.setMargin(new Insets(1, 6, 1, 6));
        delTabBtn.setToolTipText("Delete current tab");
        delTabBtn.addActionListener(this::onDeleteTab);
        rightPanel.add(delTabBtn);

        panel.add(rightPanel, BorderLayout.EAST);

        return panel;
    }

    // --- Tab management ---

    private void rebuildTabs() {
        updatingTabs = true;
        tabbedPane.removeAll();

        for (Profile profile : configManager.getProfiles()) {
            JPanel tabContent = createTabContent(profile);
            tabbedPane.addTab(profile.getName(), tabContent);
        }

        // Select active profile tab
        String active = configManager.getActiveProfileName();
        for (int i = 0; i < configManager.getProfiles().size(); i++) {
            if (configManager.getProfiles().get(i).getName().equals(active)) {
                tabbedPane.setSelectedIndex(i);
                break;
            }
        }

        if (!tabListenerInstalled) {
            tabbedPane.addChangeListener(e -> {
                if (!updatingTabs) {
                    onTabChanged();
                }
            });
            tabListenerInstalled = true;
        }

        updatingTabs = false;
    }

    private JPanel createTabContent(Profile profile) {
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        VariableTableModel model = new VariableTableModel(profile);
        JTable table = new JTable(model);
        table.setRowHeight(22);
        table.setShowGrid(true);
        table.setGridColor(new Color(220, 220, 220));
        table.getTableHeader().setReorderingAllowed(false);

        table.getColumnModel().getColumn(0).setPreferredWidth(50);  // Enabled
        table.getColumnModel().getColumn(1).setPreferredWidth(120); // Name
        table.getColumnModel().getColumn(2).setPreferredWidth(180); // Value
        table.getColumnModel().getColumn(3).setPreferredWidth(100); // Placeholder
        table.getColumnModel().getColumn(4).setPreferredWidth(80);  // Expiry
        table.getColumnModel().getColumn(5).setPreferredWidth(160); // Description

        table.getColumnModel().getColumn(2).setCellRenderer(new MaskedValueRenderer());
        table.getColumnModel().getColumn(4).setCellRenderer(new ExpiryCellRenderer());

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onEdit(null);
                }
            }
        });

        // Track which table is active
        if (profile.getName().equals(configManager.getActiveProfileName())) {
            currentTable = table;
            currentTableModel = model;
        }

        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        panel.add(createButtonPanel(table, model), BorderLayout.SOUTH);

        // Store references in client property for later access
        panel.putClientProperty("table", table);
        panel.putClientProperty("model", model);

        return panel;
    }

    private JPanel createButtonPanel(JTable table, VariableTableModel model) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));

        JButton addBtn = new JButton("Add");
        JButton editBtn = new JButton("Edit");
        JButton removeBtn = new JButton("Remove");
        JButton upBtn = new JButton("▲");
        JButton downBtn = new JButton("▼");
        JButton revealBtn = new JButton("Reveal");
        JButton clearBtn = new JButton("Clear All");
        JButton importBtn = new JButton("Import");
        JButton exportBtn = new JButton("Export");

        addBtn.addActionListener(this::onAdd);
        editBtn.addActionListener(this::onEdit);
        removeBtn.addActionListener(this::onRemove);
        upBtn.addActionListener(e -> onMoveUp(table));
        downBtn.addActionListener(e -> onMoveDown(table));
        revealBtn.addActionListener(this::onReveal);
        clearBtn.addActionListener(this::onClearAll);
        importBtn.addActionListener(this::onImport);
        exportBtn.addActionListener(this::onExport);

        panel.add(addBtn);
        panel.add(editBtn);
        panel.add(removeBtn);
        panel.add(upBtn);
        panel.add(downBtn);
        panel.add(Box.createHorizontalStrut(15));
        panel.add(revealBtn);
        panel.add(clearBtn);
        panel.add(Box.createHorizontalStrut(15));
        panel.add(importBtn);
        panel.add(exportBtn);

        return panel;
    }

    private void onTabChanged() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx < 0) return;

        List<Profile> profiles = configManager.getProfiles();
        if (idx < profiles.size()) {
            configManager.setActiveProfile(profiles.get(idx).getName());

            // Update current table reference
            JPanel tabContent = (JPanel) tabbedPane.getComponentAt(idx);
            currentTable = (JTable) tabContent.getClientProperty("table");
            currentTableModel = (VariableTableModel) tabContent.getClientProperty("model");

            updatePreview();
        }
    }

    private void onNewTab(ActionEvent e) {
        String name = JOptionPane.showInputDialog(this, "Tab name (e.g., admin, user, guest):");
        if (name != null && !name.trim().isEmpty()) {
            configManager.addProfile(name.trim());
            configManager.setActiveProfile(name.trim());
            rebuildTabs();
            updatePreview();
        }
    }

    private void onDuplicateTab(ActionEvent e) {
        String current = configManager.getActiveProfileName();
        String name = JOptionPane.showInputDialog(this, "New tab name:", current + "-copy");
        if (name != null && !name.trim().isEmpty()) {
            configManager.duplicateProfile(current, name.trim());
            configManager.setActiveProfile(name.trim());
            rebuildTabs();
            updatePreview();
        }
    }

    private void onRenameTab(ActionEvent e) {
        String current = configManager.getActiveProfileName();
        String name = JOptionPane.showInputDialog(this, "Rename tab:", current);
        if (name != null && !name.trim().isEmpty() && !name.trim().equals(current)) {
            configManager.renameProfile(current, name.trim());
            rebuildTabs();
        }
    }

    private void onDeleteTab(ActionEvent e) {
        if (configManager.getProfiles().size() <= 1) {
            JOptionPane.showMessageDialog(this, "Cannot delete the only tab.");
            return;
        }
        String current = configManager.getActiveProfileName();
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete tab '" + current + "' and all its variables?",
                "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            configManager.deleteProfile(current);
            rebuildTabs();
            updatePreview();
        }
    }

    // --- Preview section ---

    private JPanel createPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));

        JSplitPane previewSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                createRequestPanel(), createResponsePanel());
        previewSplit.setResizeWeight(0.5);

        panel.add(previewSplit, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createRequestPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JLabel label = new JLabel("  Original (placeholders)");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        panel.add(label, BorderLayout.NORTH);

        requestArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        requestArea.setLineWrap(true);
        requestArea.setWrapStyleWord(false);

        ActionMap am = requestArea.getActionMap();
        Action defaultPaste = am.get("paste-from-clipboard");
        am.put("paste-from-clipboard", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent ev) {
                try {
                    String clip = (String) Toolkit.getDefaultToolkit()
                            .getSystemClipboard().getData(DataFlavor.stringFlavor);
                    if (CurlParser.isCurl(clip)) {
                        requestArea.replaceSelection(CurlParser.toRawHttp(clip));
                        return;
                    }
                } catch (Exception ignored) {}
                defaultPaste.actionPerformed(ev);
            }
        });

        requestArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updatePreview(); }
            @Override public void removeUpdate(DocumentEvent e) { updatePreview(); }
            @Override public void changedUpdate(DocumentEvent e) { updatePreview(); }
        });

        panel.add(new JScrollPane(requestArea), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createResponsePanel() {
        JPanel panel = new JPanel(new BorderLayout());

        JLabel label = new JLabel("  Resolved (sent to server)");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        label.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        panel.add(label, BorderLayout.NORTH);

        responseArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        responseArea.setEditable(false);
        responseArea.setLineWrap(true);
        responseArea.setWrapStyleWord(false);

        panel.add(new JScrollPane(responseArea), BorderLayout.CENTER);
        return panel;
    }

    private void updatePreview() {
        previewDebounce.restart();
    }

    private void doUpdatePreview() {
        String input = requestArea.getText();
        if (configManager.isSubstitutionEnabled()) {
            responseArea.setText(placeholderService.substitute(input, configManager.getVariables()));
        } else {
            responseArea.setText(input);
        }
    }

    // --- Tool scope ---

    private void onToolScopeChanged() {
        Set<String> tools = new LinkedHashSet<>();
        for (int i = 0; i < TOOL_NAMES.length; i++) {
            if (toolCheckboxes[i].isSelected()) {
                tools.add(TOOL_NAMES[i]);
            }
        }
        configManager.setEnabledTools(tools);
    }

    // --- Variable actions ---

    private void onAdd(ActionEvent e) {
        VariableDialog dialog = new VariableDialog(SwingUtilities.getWindowAncestor(this), "Add Variable", null);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            configManager.addVariable(dialog.getVariable());
            if (currentTableModel != null) currentTableModel.fireTableDataChanged();
            updatePreview();
        }
    }

    private void onEdit(ActionEvent e) {
        if (currentTable == null) return;
        int row = currentTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a variable to edit.");
            return;
        }

        Variable existing = configManager.getVariables().get(row);
        VariableDialog dialog = new VariableDialog(SwingUtilities.getWindowAncestor(this), "Edit Variable", existing);
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            configManager.updateVariable(row, dialog.getVariable());
            if (currentTableModel != null) currentTableModel.fireTableDataChanged();
            updatePreview();
        }
    }

    private void onRemove(ActionEvent e) {
        if (currentTable == null) return;
        int row = currentTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a variable to remove.");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove '" + configManager.getVariables().get(row).getName() + "'?",
                "Confirm", JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            configManager.removeVariable(row);
            if (currentTableModel != null) currentTableModel.fireTableDataChanged();
            updatePreview();
        }
    }

    private void onMoveUp(JTable table) {
        int row = table.getSelectedRow();
        if (row <= 0) return;
        configManager.swapVariables(row, row - 1);
        if (currentTableModel != null) currentTableModel.fireTableDataChanged();
        table.setRowSelectionInterval(row - 1, row - 1);
    }

    private void onMoveDown(JTable table) {
        int row = table.getSelectedRow();
        if (row < 0 || row >= configManager.getVariables().size() - 1) return;
        configManager.swapVariables(row, row + 1);
        if (currentTableModel != null) currentTableModel.fireTableDataChanged();
        table.setRowSelectionInterval(row + 1, row + 1);
    }

    private void onReveal(ActionEvent e) {
        if (currentTable == null) return;
        int row = currentTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a variable to reveal.");
            return;
        }

        Variable var = configManager.getVariables().get(row);
        JTextArea area = new JTextArea(var.getValue());
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        area.setRows(3);
        area.setColumns(40);

        JOptionPane.showMessageDialog(this,
                new JScrollPane(area),
                "Value: <" + var.getName() + ">",
                JOptionPane.PLAIN_MESSAGE);
    }

    private void onClearAll(ActionEvent e) {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Remove ALL variables from this tab?",
                "Clear All", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            configManager.clearAll();
            if (currentTableModel != null) currentTableModel.fireTableDataChanged();
            updatePreview();
        }
    }

    private void onImport(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON files", "json"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                String json = Files.readString(chooser.getSelectedFile().toPath());
                configManager.importFromJson(json);
                if (currentTableModel != null) currentTableModel.fireTableDataChanged();
                updatePreview();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                        "Import Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onExport(ActionEvent e) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("reqvars-" + configManager.getActiveProfileName() + ".json"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.writeString(chooser.getSelectedFile().toPath(), configManager.exportToJson());
                JOptionPane.showMessageDialog(this, "Exported.");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage(),
                        "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- Table model ---

    private class VariableTableModel extends AbstractTableModel {

        private final Profile profile;
        private final String[] columns = {"Enabled", "Name", "Value", "Placeholder", "Expiry", "Description"};

        VariableTableModel(Profile profile) {
            this.profile = profile;
        }

        @Override
        public int getRowCount() {
            return profile.getVariables().size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) return Boolean.class;
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 0;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Variable var = profile.getVariables().get(rowIndex);
            return switch (columnIndex) {
                case 0 -> var.isEnabled();
                case 1 -> var.getName();
                case 2 -> var.getMaskedValue();
                case 3 -> var.getPlaceholder();
                case 4 -> var.getExpiryStatus();
                case 5 -> var.getDescription();
                default -> null;
            };
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                Variable var = profile.getVariables().get(rowIndex);
                var.setEnabled((Boolean) aValue);
                configManager.updateVariable(rowIndex, var);
                updatePreview();
            }
        }
    }

    // --- Cell renderers ---

    private static class MaskedValueRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setFont(getFont().deriveFont(Font.ITALIC));
            setForeground(isSelected ? table.getSelectionForeground() : Color.GRAY);
            return this;
        }
    }

    private static class ExpiryCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            String text = value != null ? value.toString() : "";
            if ("EXPIRED".equals(text)) {
                setForeground(new Color(220, 50, 50));
                setFont(getFont().deriveFont(Font.BOLD));
            } else if (text.contains("left")) {
                setForeground(new Color(200, 150, 0));
                setFont(getFont().deriveFont(Font.PLAIN));
            } else {
                setForeground(isSelected ? table.getSelectionForeground() : Color.GRAY);
                setFont(getFont().deriveFont(Font.PLAIN));
            }
            return this;
        }
    }

    // --- Variable dialog ---

    private static class VariableDialog extends JDialog {

        private final JTextField nameField = new JTextField(20);
        private final JPasswordField valueField = new JPasswordField(30);
        private final JTextField descField = new JTextField(30);
        private final JCheckBox enabledBox = new JCheckBox("Enabled", true);
        private final JCheckBox showValueBox = new JCheckBox("Show value");
        private final JCheckBox autoExpiryBox = new JCheckBox("Auto-detect JWT expiry", true);
        private final JTextField expiryField = new JTextField(12);
        private boolean confirmed = false;
        private Long manualExpiry = null;

        public VariableDialog(Window owner, String title, Variable existing) {
            super(owner, title, ModalityType.APPLICATION_MODAL);
            setLayout(new BorderLayout(10, 10));
            setResizable(false);

            JPanel form = new JPanel(new GridBagLayout());
            form.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(4, 4, 4, 4);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;

            gbc.gridx = 0; gbc.gridy = 0;
            form.add(new JLabel("Name:"), gbc);
            gbc.gridx = 1;
            form.add(nameField, gbc);

            gbc.gridx = 0; gbc.gridy = 1;
            form.add(new JLabel("Value:"), gbc);
            gbc.gridx = 1;
            JPanel valuePanel = new JPanel(new BorderLayout(5, 0));
            valuePanel.add(valueField, BorderLayout.CENTER);
            showValueBox.addActionListener(e ->
                    valueField.setEchoChar(showValueBox.isSelected() ? (char) 0 : '•'));
            valuePanel.add(showValueBox, BorderLayout.EAST);
            form.add(valuePanel, gbc);

            gbc.gridx = 0; gbc.gridy = 2;
            form.add(new JLabel("Description:"), gbc);
            gbc.gridx = 1;
            form.add(descField, gbc);

            gbc.gridx = 0; gbc.gridy = 3;
            form.add(new JLabel("Expiry:"), gbc);
            gbc.gridx = 1;
            JPanel expiryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            expiryField.setToolTipText("Unix timestamp (epoch seconds), or leave empty");
            expiryPanel.add(expiryField);
            expiryPanel.add(autoExpiryBox);
            form.add(expiryPanel, gbc);

            gbc.gridx = 0; gbc.gridy = 4;
            form.add(new JLabel(""), gbc);
            gbc.gridx = 1;
            form.add(enabledBox, gbc);

            if (existing != null) {
                nameField.setText(existing.getName());
                valueField.setText(existing.getValue());
                descField.setText(existing.getDescription());
                enabledBox.setSelected(existing.isEnabled());
                if (existing.getExpiresAt() != null) {
                    expiryField.setText(String.valueOf(existing.getExpiresAt()));
                }
            }

            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton okBtn = new JButton("OK");
            JButton cancelBtn = new JButton("Cancel");

            okBtn.addActionListener(e -> {
                if (nameField.getText().trim().isEmpty()) {
                    JOptionPane.showMessageDialog(this, "Name is required.");
                    return;
                }
                if (!nameField.getText().trim().matches("[a-zA-Z_][a-zA-Z0-9_-]*")) {
                    JOptionPane.showMessageDialog(this, "Invalid name.");
                    return;
                }
                String expStr = expiryField.getText().trim();
                if (!expStr.isEmpty()) {
                    try {
                        manualExpiry = Long.parseLong(expStr);
                    } catch (NumberFormatException ex) {
                        JOptionPane.showMessageDialog(this, "Expiry must be a unix timestamp.");
                        return;
                    }
                }
                confirmed = true;
                dispose();
            });
            cancelBtn.addActionListener(e -> dispose());

            buttons.add(okBtn);
            buttons.add(cancelBtn);

            add(form, BorderLayout.CENTER);
            add(buttons, BorderLayout.SOUTH);
            pack();
            setLocationRelativeTo(owner);
        }

        public boolean isConfirmed() {
            return confirmed;
        }

        public Variable getVariable() {
            String value = new String(valueField.getPassword());
            Long expiry = manualExpiry;
            if (expiry == null && autoExpiryBox.isSelected()) {
                expiry = Variable.extractJwtExpiry(value);
            }
            return new Variable(
                    nameField.getText().trim(),
                    value,
                    descField.getText().trim(),
                    enabledBox.isSelected(),
                    expiry
            );
        }
    }
}

package org.trostheide.watercolorprocessor.gui;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

public class SettingsPanel extends JPanel {

    // General Settings (Moved from PlotterPanel)
    private final JSpinner speedDownSpinner;
    private final JSpinner speedUpSpinner;
    private final JSpinner zUpSpinner;
    private final JSpinner zDownSpinner;
    private final JComboBox<String> modelComboBox;

    // Station Editor Components
    private final JTable stationTable;
    private final DefaultTableModel tableModel;

    private final JTextField idField;
    private final JSpinner xSpinner;
    private final JSpinner ySpinner;
    private final JSpinner zSpinner;
    private final JComboBox<String> behaviorCombo;

    // In-memory store
    private final Map<String, StationConfig> stations = new LinkedHashMap<>();
    private final File configFile = new File("stations.json");
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    // Validation/Control components for mass enabling/disabling
    private final JButton addBtn;
    private final JButton removeBtn;
    private final JButton saveFileBtn;

    // Callback for running driver commands (e.g. manual pen toggle)
    // We pass a List of Strings (args basically)
    private java.util.function.Consumer<java.util.List<String>> driverRunner;

    public SettingsPanel() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // --- NORTH: General Settings ---
        JPanel generalPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 10));
        generalPanel.setBorder(BorderFactory.createTitledBorder("General Plotter Settings"));

        // Plotter Size
        generalPanel.add(new JLabel("Plotter Size:"));
        modelComboBox = new JComboBox<>(new String[] { "Standard (A4 / V3)", "Large (A3 / V3 XL)" });
        modelComboBox.setSelectedIndex(1); // Default to A3
        generalPanel.add(modelComboBox);

        // Speed Down
        generalPanel.add(new JLabel("Draw Speed (%):"));
        speedDownSpinner = new JSpinner(new SpinnerNumberModel(25, 1, 100, 1));
        generalPanel.add(speedDownSpinner);

        // Speed Up
        generalPanel.add(new JLabel("Travel Speed (%):"));
        speedUpSpinner = new JSpinner(new SpinnerNumberModel(75, 1, 100, 1));
        generalPanel.add(speedUpSpinner);

        // Pen Up
        generalPanel.add(new JLabel("Pen Up (%):"));
        zUpSpinner = new JSpinner(new SpinnerNumberModel(60, 0, 100, 1));
        generalPanel.add(zUpSpinner);

        // Pen Down
        generalPanel.add(new JLabel("Pen Down (%):"));
        zDownSpinner = new JSpinner(new SpinnerNumberModel(30, 0, 100, 1));
        generalPanel.add(zDownSpinner);

        // Test Buttons
        JButton testUpBtn = new JButton("Test UP");
        testUpBtn.addActionListener(e -> runManualPen("UP"));
        generalPanel.add(testUpBtn);

        JButton testDownBtn = new JButton("Test DOWN");
        testDownBtn.addActionListener(e -> runManualPen("DOWN"));
        generalPanel.add(testDownBtn);

        add(generalPanel, BorderLayout.NORTH);

        // --- CENTER: Station Table ---
        tableModel = new DefaultTableModel(new String[] { "ID", "X (mm)", "Y (mm)", "Z Down", "Behavior" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        stationTable = new JTable(tableModel);
        stationTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        stationTable.getSelectionModel().addListSelectionListener(e -> loadSelection());

        JScrollPane scrollPane = new JScrollPane(stationTable);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Configured Stations"));
        add(scrollPane, BorderLayout.CENTER);

        // --- EAST: Station Editor Form ---
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Edit Station"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        formPanel.add(new JLabel("ID:"), gbc);
        gbc.gridx = 1;
        idField = new JTextField(10);
        formPanel.add(idField, gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        formPanel.add(new JLabel("X (mm):"), gbc);
        gbc.gridx = 1;
        xSpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 300.0, 1.0));
        formPanel.add(xSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 2;
        formPanel.add(new JLabel("Y (mm):"), gbc);
        gbc.gridx = 1;
        ySpinner = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 300.0, 1.0));
        formPanel.add(ySpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        formPanel.add(new JLabel("Z Down (%):"), gbc);
        gbc.gridx = 1;
        zSpinner = new JSpinner(new SpinnerNumberModel(30, 0, 100, 1));
        formPanel.add(zSpinner, gbc);

        gbc.gridx = 0;
        gbc.gridy = 4;
        formPanel.add(new JLabel("Behavior:"), gbc);
        gbc.gridx = 1;
        behaviorCombo = new JComboBox<>(new String[] { "simple_dip", "dip_swirl" });
        formPanel.add(behaviorCombo, gbc);

        // Buttons
        JPanel btnPanel = new JPanel(new FlowLayout());
        addBtn = new JButton("Add / Update");
        addBtn.addActionListener(e -> saveStation());
        removeBtn = new JButton("Remove");
        removeBtn.addActionListener(e -> removeStation());

        btnPanel.add(addBtn);
        btnPanel.add(removeBtn);

        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.gridwidth = 2;
        formPanel.add(btnPanel, gbc);

        JPanel rightContainer = new JPanel(new BorderLayout());
        rightContainer.add(formPanel, BorderLayout.NORTH);

        // Save to File Button
        saveFileBtn = new JButton("Save Configuration to Disk");
        saveFileBtn.setFont(saveFileBtn.getFont().deriveFont(Font.BOLD));
        saveFileBtn.addActionListener(e -> saveToFile());
        rightContainer.add(saveFileBtn, BorderLayout.SOUTH);

        add(rightContainer, BorderLayout.EAST);

        // Initial Load
        loadFromFile();
    }

    // --- Public Accessors for PlotterPanel ---
    public int getDrawSpeed() {
        return (Integer) speedDownSpinner.getValue();
    }

    public int getTravelSpeed() {
        return (Integer) speedUpSpinner.getValue();
    }

    // Index 0 -> Model 1 (A4), Index 1 -> Model 2 (A3)
    public int getPlotterModelIndex() {
        return modelComboBox.getSelectedIndex();
    }

    public int getPenUpHeight() {
        return (Integer) zUpSpinner.getValue();
    }

    public int getPenDownHeight() {
        return (Integer) zDownSpinner.getValue();
    }

    public void setSettingsEnabled(boolean enabled) {
        modelComboBox.setEnabled(enabled);
        speedDownSpinner.setEnabled(enabled);
        speedUpSpinner.setEnabled(enabled);
        zUpSpinner.setEnabled(enabled);
        zDownSpinner.setEnabled(enabled);

        stationTable.setEnabled(enabled);
        idField.setEnabled(enabled);
        xSpinner.setEnabled(enabled);
        ySpinner.setEnabled(enabled);
        zSpinner.setEnabled(enabled);
        behaviorCombo.setEnabled(enabled);
        addBtn.setEnabled(enabled);
        removeBtn.setEnabled(enabled);
        saveFileBtn.setEnabled(enabled);
    }

    public void setDriverRunner(java.util.function.Consumer<java.util.List<String>> driverRunner) {
        this.driverRunner = driverRunner;
    }

    // --- Internal Logic (Same as StationEditorPanel) ---

    private void loadSelection() {
        int row = stationTable.getSelectedRow();
        if (row >= 0) {
            String id = (String) tableModel.getValueAt(row, 0);
            StationConfig cfg = stations.get(id);
            if (cfg != null) {
                idField.setText(id);
                xSpinner.setValue(cfg.x);
                ySpinner.setValue(cfg.y);
                zSpinner.setValue(cfg.z_down);
                behaviorCombo.setSelectedItem(cfg.behavior);
            }
        }
    }

    private void saveStation() {
        String id = idField.getText().trim();
        if (id.isEmpty())
            return;

        StationConfig cfg = new StationConfig(
                (Double) xSpinner.getValue(),
                (Double) ySpinner.getValue(),
                (Integer) zSpinner.getValue(),
                (String) behaviorCombo.getSelectedItem());

        stations.put(id, cfg);
        refreshTable();
    }

    private void removeStation() {
        String id = idField.getText().trim();
        if (stations.containsKey(id)) {
            stations.remove(id);
            refreshTable();
            idField.setText("");
        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        stations.forEach((id, cfg) -> {
            tableModel.addRow(new Object[] { id, cfg.x, cfg.y, cfg.z_down, cfg.behavior });
        });
    }

    private void loadFromFile() {
        if (configFile.exists()) {
            try {
                Map<String, Object> map = mapper.readValue(configFile, Map.class);
                stations.clear();
                map.forEach((k, v) -> {
                    Map<String, Object> val = (Map<String, Object>) v;
                    stations.put(k, new StationConfig(
                            getDouble(val.get("x")),
                            getDouble(val.get("y")),
                            getInt(val.get("z_down")),
                            (String) val.get("behavior")));
                });
                refreshTable();
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Failed to load config: " + e.getMessage());
            }
        } else {
            // Defaults
            stations.put("default_station", new StationConfig(5.0, 50.0, 20, "simple_dip"));
            refreshTable();
        }
    }

    private double getDouble(Object o) {
        if (o instanceof Number)
            return ((Number) o).doubleValue();
        return 0.0;
    }

    private int getInt(Object o) {
        if (o instanceof Number)
            return ((Number) o).intValue();
        return 0;
    }

    private void saveToFile() {
        try {
            mapper.writeValue(configFile, stations);
            JOptionPane.showMessageDialog(this, "Configuration saved to " + configFile.getAbsolutePath());
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage());
        }
    }

    private void runManualPen(String direction) {
        if (driverRunner != null) {
            java.util.List<String> extraArgs = new java.util.ArrayList<>();
            extraArgs.add("--manual-pen");
            extraArgs.add(direction);

            // Pass current settings to ensure they are used
            extraArgs.add("--pen-up");
            extraArgs.add(String.valueOf(getPenUpHeight()));
            extraArgs.add("--pen-down");
            extraArgs.add(String.valueOf(getPenDownHeight()));

            driverRunner.accept(extraArgs);
        }
    }

    // DTO
    record StationConfig(double x, double y, int z_down, String behavior) {
    }
}

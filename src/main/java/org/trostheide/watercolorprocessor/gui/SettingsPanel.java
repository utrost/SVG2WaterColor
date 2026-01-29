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
    private JSpinner zUpSpinner;
    private JSpinner zDownSpinner;
    private final JCheckBox invertXCheckBox;
    private final JCheckBox invertYCheckBox;
    private final JCheckBox swapXYCheckBox;
    private JRadioButton portraitRadio; // New Field for Orientation
    private JRadioButton landscapeRadio;
    private final JCheckBox visualMirrorCheckBox;
    private final JComboBox<String> modelComboBox;
    private final JCheckBox mockCheckBox;
    private final JComboBox<String> canvasAlignmentCombo;
    private final JComboBox<String> viewRotationCombo; // New Field

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
    private final File legacyStationFile = new File("stations.json");
    private File currentConfigFile = new File("config.json");
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    // DTOs for Full Config
    record GeneralSettings(int modelIndex, boolean mock, boolean invertX, boolean invertY, boolean swapXY,
            boolean visualMirror, int speedDown, int speedUp, int penUp, int penDown,
            String orientation, String canvasAlignment, int viewRotation) {
    }

    record AppConfig(GeneralSettings general, Map<String, StationConfig> stations) {
    }

    // Validation/Control components for mass enabling/disabling
    private final JButton addBtn;
    private final JButton removeBtn;
    private final JButton saveFileBtn;
    private final JButton loadFileBtn;
    private final JLabel activeConfigLabel;

    // Callback for running driver commands
    public interface ManualControlSession {
        void sendRelativeMove(double dx, double dy);

        void sendPenCommand(String direction, int height);

        void resetServer();
    }

    private ManualControlSession manualSession;
    private Runnable visualChangeListener;

    public void addVisualChangeListener(Runnable listener) {
        this.visualChangeListener = listener;
    }

    private void fireVisualChange() {
        if (visualChangeListener != null)
            visualChangeListener.run();
    }

    public SettingsPanel() {
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // --- NORTH: General Settings ---
        JPanel generalPanel = new JPanel(new GridBagLayout());
        generalPanel.setBorder(BorderFactory.createTitledBorder("General Plotter Settings"));
        GridBagConstraints gbcGen = new GridBagConstraints();
        gbcGen.insets = new Insets(5, 10, 5, 10);
        gbcGen.anchor = GridBagConstraints.WEST;
        gbcGen.fill = GridBagConstraints.NONE;

        // Row 1: Model & Orientation Flags
        gbcGen.gridx = 0;
        gbcGen.gridy = 0;

        // Plotter Size
        JPanel modelPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        modelPanel.add(new JLabel("Plotter Size:"));
        modelComboBox = new JComboBox<>(new String[] { "Standard (A4 / V3)", "Large (A3 / V3 XL)" });
        modelComboBox.setSelectedIndex(1); // Default to A3
        modelPanel.add(modelComboBox);
        generalPanel.add(modelPanel, gbcGen);

        // Flags
        gbcGen.gridx = 1;
        gbcGen.weightx = 1.0;
        JPanel flagsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));

        // Mock Mode
        mockCheckBox = new JCheckBox("Mock Mode", false);
        flagsPanel.add(mockCheckBox);

        // Invert X
        invertXCheckBox = new JCheckBox("Invert X");
        invertXCheckBox.addActionListener(e -> {
            if (manualSession != null)
                manualSession.resetServer();
        });
        flagsPanel.add(invertXCheckBox);

        // Invert Y
        invertYCheckBox = new JCheckBox("Invert Y", false);
        invertYCheckBox.addActionListener(e -> {
            if (manualSession != null)
                manualSession.resetServer();
        });
        flagsPanel.add(invertYCheckBox);

        // Swap XY
        swapXYCheckBox = new JCheckBox("Swap X/Y", true);
        swapXYCheckBox.addActionListener(e -> {
            if (manualSession != null)
                manualSession.resetServer();
            fireVisualChange();
        });
        flagsPanel.add(swapXYCheckBox);

        // Visual Mirror
        JCheckBox visualMirrorCheckBox = new JCheckBox("View: 0,0 Top-Right", true);
        visualMirrorCheckBox.addActionListener(e -> fireVisualChange());
        flagsPanel.add(visualMirrorCheckBox);
        this.visualMirrorCheckBox = visualMirrorCheckBox;

        generalPanel.add(flagsPanel, gbcGen);

        // Row 1: Machine Orientation
        gbcGen.gridx = 0;
        gbcGen.gridy = 1;
        gbcGen.weightx = 0.0;

        JPanel orientationPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        orientationPanel.add(new JLabel("Orientation:"));
        landscapeRadio = new JRadioButton("Landscape");
        portraitRadio = new JRadioButton("Portrait");
        ButtonGroup orientationGroup = new ButtonGroup();
        orientationGroup.add(landscapeRadio);
        orientationGroup.add(portraitRadio);
        portraitRadio.setSelected(true); // Default (User Request)

        landscapeRadio.addActionListener(e -> fireVisualChange());
        portraitRadio.addActionListener(e -> fireVisualChange());

        orientationPanel.add(landscapeRadio);
        orientationPanel.add(portraitRadio);
        generalPanel.add(orientationPanel, gbcGen);

        // Row 1 (Right): Canvas Alignment
        gbcGen.gridx = 1;
        gbcGen.gridy = 1;
        gbcGen.weightx = 1.0;

        JPanel alignPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        alignPanel.add(new JLabel("Canvas Alignment:"));
        canvasAlignmentCombo = new JComboBox<>(new String[] {
                "Top Left", "Top Right", "Bottom Left", "Bottom Right", "Center"
        });
        canvasAlignmentCombo.setSelectedItem("Top Right"); // Default
        canvasAlignmentCombo.setToolTipText("Align the drawing to a specific corner of the machine bed.");
        canvasAlignmentCombo.addActionListener(e -> fireVisualChange());
        alignPanel.add(canvasAlignmentCombo);

        // Rotation
        alignPanel.add(new JLabel("Rot:"));
        viewRotationCombo = new JComboBox<>(new String[] { "0", "90", "180", "270" });
        viewRotationCombo.setToolTipText("Rotate view (CCW)");
        viewRotationCombo.addActionListener(e -> fireVisualChange());
        alignPanel.add(viewRotationCombo);

        generalPanel.add(alignPanel, gbcGen);

        // Row 3: Speeds
        gbcGen.gridx = 0;
        gbcGen.gridy = 2;
        gbcGen.gridwidth = 2;
        gbcGen.weightx = 0.0;

        JPanel speedPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0));

        // Speed Down
        speedPanel.add(new JLabel("Draw Speed (%):"));
        speedDownSpinner = new JSpinner(new SpinnerNumberModel(25, 1, 100, 1));
        speedPanel.add(speedDownSpinner);

        // Speed Up
        speedPanel.add(new JLabel("Travel Speed (%):"));
        speedUpSpinner = new JSpinner(new SpinnerNumberModel(75, 1, 100, 1));
        speedPanel.add(speedUpSpinner);

        generalPanel.add(speedPanel, gbcGen);

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

        // --- NEW: Manual Controls Panel ---
        JPanel manualPanel = new JPanel(new GridBagLayout());
        manualPanel.setBorder(BorderFactory.createTitledBorder("Manual Control"));
        GridBagConstraints mgbc = new GridBagConstraints();
        mgbc.insets = new Insets(5, 5, 5, 5);
        mgbc.fill = GridBagConstraints.BOTH;

        // Step Size
        mgbc.gridx = 0;
        mgbc.gridy = 0;
        mgbc.gridwidth = 3;
        JPanel stepPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        stepPanel.add(new JLabel("Step (mm):"));
        JSpinner stepSpinner = new JSpinner(new SpinnerNumberModel(10.0, 0.1, 100.0, 1.0));
        stepPanel.add(stepSpinner);
        manualPanel.add(stepPanel, mgbc);

        // Direction Buttons
        JButton btnUp = new JButton("▲ (-Y)");
        JButton btnDown = new JButton("▼ (+Y)");
        JButton btnLeft = new JButton("◀ (+X)");
        JButton btnRight = new JButton("▶ (-X)");

        // Actions
        java.awt.event.ActionListener moveAction = e -> {
            double step = (Double) stepSpinner.getValue();
            double dx = 0, dy = 0;
            if (e.getSource() == btnUp)
                dy = -step; // User: Y- goes up
            else if (e.getSource() == btnDown)
                dy = step; // User: Y+ goes down
            else if (e.getSource() == btnLeft)
                dx = step; // User: X+ goes left
            else if (e.getSource() == btnRight)
                dx = -step; // User: X- goes right
            runManualMove(dx, dy);
        };

        btnUp.addActionListener(moveAction);
        btnDown.addActionListener(moveAction);
        btnLeft.addActionListener(moveAction);
        btnRight.addActionListener(moveAction);

        // Layout Buttons (Cross shape)
        mgbc.gridwidth = 1;
        mgbc.gridx = 1;
        mgbc.gridy = 1;
        manualPanel.add(btnUp, mgbc); // Top
        mgbc.gridx = 0;
        mgbc.gridy = 2;
        manualPanel.add(btnLeft, mgbc); // Left
        mgbc.gridx = 2;
        mgbc.gridy = 2;
        manualPanel.add(btnRight, mgbc);// Right
        mgbc.gridx = 1;
        mgbc.gridy = 3;
        manualPanel.add(btnDown, mgbc); // Bottom

        mgbc.gridy = 3;
        manualPanel.add(btnDown, mgbc); // Bottom

        // Pen Height Config
        mgbc.gridx = 0;
        mgbc.gridy = 4;
        mgbc.gridwidth = 3;
        JPanel penConfigPanel = new JPanel(new FlowLayout());

        // We need to re-initialize spinners here? No, they are final fields.
        // But we need to make sure they are not added twice? We removed them from TOP.
        // But wait, the lines removed above (120-129) included the initialization
        // `zUpSpinner = ...`.
        // If I remove the initialization lines, `zUpSpinner` will be null!
        // CORRECTION: I must initialize them before adding them here.
        // Actually, the previous 'remove' block deleted the initialization. I need to
        // re-add initialization logic here or keep it earlier.
        // Better: Initialize them earlier (top of constructor) or just here.

        penConfigPanel.add(new JLabel("Up %:"));
        zUpSpinner = new JSpinner(new SpinnerNumberModel(60, 0, 100, 1));
        penConfigPanel.add(zUpSpinner);

        penConfigPanel.add(new JLabel("Down %:"));
        zDownSpinner = new JSpinner(new SpinnerNumberModel(30, 0, 100, 1));
        penConfigPanel.add(zDownSpinner);

        manualPanel.add(penConfigPanel, mgbc);

        // Pen Controls
        mgbc.gridx = 0;
        mgbc.gridy = 5;
        mgbc.gridwidth = 3;
        JPanel penBtnPanel = new JPanel(new FlowLayout());
        JButton testUpBtn = new JButton("Pen UP");
        testUpBtn.addActionListener(e -> runManualPen("UP"));
        JButton testDownBtn = new JButton("Pen DOWN");
        testDownBtn.addActionListener(e -> runManualPen("DOWN"));
        penBtnPanel.add(testUpBtn);
        penBtnPanel.add(testDownBtn);
        manualPanel.add(penBtnPanel, mgbc);

        // Key Bindings
        InputMap im = manualPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = manualPanel.getActionMap();

        im.put(KeyStroke.getKeyStroke("UP"), "moveUp");
        im.put(KeyStroke.getKeyStroke("DOWN"), "moveDown");
        im.put(KeyStroke.getKeyStroke("LEFT"), "moveLeft");
        im.put(KeyStroke.getKeyStroke("RIGHT"), "moveRight");

        am.put("moveUp", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                runManualMove(0, -(Double) stepSpinner.getValue());
            }
        });
        am.put("moveDown", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                runManualMove(0, (Double) stepSpinner.getValue());
            }
        });
        am.put("moveLeft", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                runManualMove((Double) stepSpinner.getValue(), 0);
            }
        });
        am.put("moveRight", new AbstractAction() {
            public void actionPerformed(java.awt.event.ActionEvent e) {
                runManualMove(-(Double) stepSpinner.getValue(), 0);
            }
        });

        // --- EAST Container Assembly ---
        JPanel rightContainer = new JPanel(new BorderLayout());
        JPanel topRight = new JPanel(new BorderLayout());
        topRight.add(formPanel, BorderLayout.NORTH);
        topRight.add(manualPanel, BorderLayout.CENTER);

        rightContainer.add(topRight, BorderLayout.NORTH);

        // Save to File Button
        // Save/Load Config
        JPanel fileBtnPanel = new JPanel(new GridLayout(3, 1, 5, 5));

        activeConfigLabel = new JLabel("Config: " + currentConfigFile.getName());
        activeConfigLabel.setHorizontalAlignment(SwingConstants.CENTER);
        fileBtnPanel.add(activeConfigLabel);

        saveFileBtn = new JButton("Save Config As...");
        saveFileBtn.setFont(saveFileBtn.getFont().deriveFont(Font.BOLD));
        saveFileBtn.addActionListener(e -> saveAs());

        loadFileBtn = new JButton("Load Config...");
        loadFileBtn.addActionListener(e -> loadFrom());

        fileBtnPanel.add(saveFileBtn);
        fileBtnPanel.add(loadFileBtn);

        rightContainer.add(fileBtnPanel, BorderLayout.SOUTH);

        add(rightContainer, BorderLayout.EAST);

        // Initial Load
        // Initial Load
        loadConfig();

        // --- Listeners for Auto-Restart on Spinner Change ---
        // Moved here to ensure all components (especially zUp/zDown) are initialized.
        javax.swing.event.ChangeListener valueChange = e -> {
            if (manualSession != null)
                manualSession.resetServer();
        };

        speedDownSpinner.addChangeListener(valueChange);
        speedUpSpinner.addChangeListener(valueChange);
        zUpSpinner.addChangeListener(valueChange);
        zDownSpinner.addChangeListener(valueChange);
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

    public boolean isMockMode() {
        return mockCheckBox.isSelected();
    }

    public boolean isInvertX() {
        return invertXCheckBox.isSelected();
    }

    public boolean isInvertY() {
        return invertYCheckBox.isSelected();
    }

    public boolean isSwapXY() {
        return swapXYCheckBox.isSelected();
    }

    public boolean isVisualMirror() {
        return visualMirrorCheckBox.isSelected();
    }

    public String getCanvasAlignment() {
        return (String) canvasAlignmentCombo.getSelectedItem();
    }

    public String getOrientation() {
        if (portraitRadio != null && portraitRadio.isSelected())
            return "Portrait";
        return "Landscape";
    }

    public int getViewRotation() {
        try {
            return Integer.parseInt((String) viewRotationCombo.getSelectedItem());
        } catch (Exception e) {
            return 0;
        }
    }

    public double getMachineWidth() {
        boolean isPortrait = portraitRadio != null && portraitRadio.isSelected();
        double w;
        if (getPlotterModelIndex() == 0)
            w = 297; // A4 Width (Landscape)
        else
            w = 430; // A3 Width (Landscape)

        return isPortrait ? getMachineHeightLimit() : w;
    }

    private double getMachineHeightLimit() {
        if (getPlotterModelIndex() == 0)
            return 210;
        return 297;
    }

    public double getMachineHeight() {
        boolean isPortrait = portraitRadio != null && portraitRadio.isSelected();

        if (isPortrait) {
            return (getPlotterModelIndex() == 0) ? 297 : 430;
        }
        return getMachineHeightLimit();
    }

    public void setSettingsEnabled(boolean enabled) {
        modelComboBox.setEnabled(enabled);
        speedDownSpinner.setEnabled(enabled);
        speedUpSpinner.setEnabled(enabled);
        zUpSpinner.setEnabled(enabled);
        zDownSpinner.setEnabled(enabled);
        mockCheckBox.setEnabled(enabled);
        invertXCheckBox.setEnabled(enabled);
        invertYCheckBox.setEnabled(enabled);
        swapXYCheckBox.setEnabled(enabled);
        visualMirrorCheckBox.setEnabled(enabled);

        stationTable.setEnabled(enabled);
        idField.setEnabled(enabled);
        xSpinner.setEnabled(enabled);
        ySpinner.setEnabled(enabled);
        zSpinner.setEnabled(enabled);
        behaviorCombo.setEnabled(enabled);
        addBtn.setEnabled(enabled);
        removeBtn.setEnabled(enabled);
        saveFileBtn.setEnabled(enabled);
        loadFileBtn.setEnabled(enabled);

    }

    public void setManualSession(ManualControlSession session) {
        this.manualSession = session;
    }

    public File getCurrentConfigFile() {
        return currentConfigFile;
    }

    public Map<String, StationConfig> getStations() {
        return stations;
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
        fireVisualChange(); // Refresh station markers in visualization
    }

    private void removeStation() {
        String id = idField.getText().trim();
        if (stations.containsKey(id)) {
            stations.remove(id);
            refreshTable();
            idField.setText("");
            fireVisualChange(); // Refresh station markers in visualization
        }
    }

    private void refreshTable() {
        tableModel.setRowCount(0);
        stations.forEach((id, cfg) -> {
            tableModel.addRow(new Object[] { id, cfg.x, cfg.y, cfg.z_down, cfg.behavior });
        });
    }

    private void loadConfig() {
        if (currentConfigFile.exists()) {
            try {
                AppConfig config = mapper.readValue(currentConfigFile, AppConfig.class);

                // Load General Settings
                if (config.general != null) {
                    modelComboBox.setSelectedIndex(config.general.modelIndex);
                    mockCheckBox.setSelected(config.general.mock);
                    invertXCheckBox.setSelected(config.general.invertX);
                    invertYCheckBox.setSelected(config.general.invertY);
                    swapXYCheckBox.setSelected(config.general.swapXY);
                    visualMirrorCheckBox.setSelected(config.general.visualMirror);
                    speedDownSpinner.setValue(config.general.speedDown);
                    speedUpSpinner.setValue(config.general.speedUp);
                    zUpSpinner.setValue(config.general.penUp);
                    zDownSpinner.setValue(config.general.penDown);

                    // Restore Orientation & Alignment (with safe defaults)
                    if ("Portrait".equals(config.general.orientation)) {
                        portraitRadio.setSelected(true);
                    } else {
                        landscapeRadio.setSelected(true);
                    }

                    if (config.general.canvasAlignment != null) {
                        canvasAlignmentCombo.setSelectedItem(config.general.canvasAlignment);
                    }

                    if (config.general.viewRotation > 0) {
                        viewRotationCombo.setSelectedItem(String.valueOf(config.general.viewRotation));
                    }
                }

                // Load Stations
                stations.clear();
                if (config.stations != null) {
                    stations.putAll(config.stations);
                }
                refreshTable();

            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Failed to load config: " + e.getMessage());
            }
        } else if (legacyStationFile.exists()) {
            // Fallback to legacy stations.json
            try {
                Map<String, Object> map = mapper.readValue(legacyStationFile, Map.class);
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
                JOptionPane.showMessageDialog(this, "Failed to load legacy stations.json: " + e.getMessage());
            }
        } else {
            // Defaults
            stations.put("default_station", new StationConfig(5.0, 50.0, 20, "simple_dip"));
            refreshTable();
        }

        // Ensure visual state is updated after load
        fireVisualChange();
        updateConfigLabel();
    }

    private void updateConfigLabel() {
        if (activeConfigLabel != null) {
            activeConfigLabel.setText("Config: " + currentConfigFile.getName());
            activeConfigLabel.setToolTipText(currentConfigFile.getAbsolutePath());
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

    private void saveAs() {
        JFileChooser fc = new JFileChooser(".");
        fc.setSelectedFile(currentConfigFile);
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentConfigFile = fc.getSelectedFile();
            saveConfig();
            updateConfigLabel();
        }
    }

    private void loadFrom() {
        JFileChooser fc = new JFileChooser(".");
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            currentConfigFile = fc.getSelectedFile();
            loadConfig();
            fireVisualChange(); // Refresh station markers in visualization
        }
    }

    public void saveConfigSilent() {
        saveConfig(false);
    }

    private void saveConfig() {
        saveConfig(true);
    }

    private void saveConfig(boolean showMessage) {
        try {
            GeneralSettings gen = new GeneralSettings(
                    modelComboBox.getSelectedIndex(),
                    mockCheckBox.isSelected(),
                    invertXCheckBox.isSelected(),
                    invertYCheckBox.isSelected(),
                    swapXYCheckBox.isSelected(),
                    visualMirrorCheckBox.isSelected(),
                    (Integer) speedDownSpinner.getValue(),
                    (Integer) speedUpSpinner.getValue(),
                    (Integer) zUpSpinner.getValue(),
                    (Integer) zDownSpinner.getValue(),
                    getOrientation(),
                    getCanvasAlignment(),
                    getViewRotation());

            AppConfig config = new AppConfig(gen, new LinkedHashMap<>(stations));

            mapper.writeValue(currentConfigFile, config);
            if (showMessage) {
                JOptionPane.showMessageDialog(this,
                        "Configuration saved to " + currentConfigFile.getAbsolutePath());
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (showMessage) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage());
            }
        }
    }

    private void runManualPen(String direction) {
        if (manualSession != null) {
            int height = "UP".equals(direction) ? getPenUpHeight() : getPenDownHeight();
            manualSession.sendPenCommand(direction, height);
        }
    }

    private void runManualMove(double dx, double dy) {
        if (manualSession != null) {
            manualSession.sendRelativeMove(dx, dy);
        }
    }

    // DTO
    record StationConfig(double x, double y, int z_down, String behavior) {
    }
}

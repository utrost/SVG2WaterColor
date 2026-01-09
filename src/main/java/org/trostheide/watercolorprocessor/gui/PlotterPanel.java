package org.trostheide.watercolorprocessor.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class PlotterPanel extends JPanel {

    private final JTextField jsonField;
    private final JTextField pythonPathField;
    // Model Selection moved to SettingsPanel
    // Model Selection moved to SettingsPanel
    private final JCheckBox verboseCheckBox;
    // Speed spinners moved to SettingsPanel
    private final SettingsPanel settingsPanel;
    private final JTextArea consoleArea;
    private final VisualizationPanel visPanel; // New Visualizer
    private final JButton startButton;
    private final JButton stopButton;
    private final JButton inputButton;

    private Process currentProcess;
    private BufferedWriter processInputWriter;

    public PlotterPanel(SettingsPanel settingsPanel) {
        this.settingsPanel = settingsPanel;

        // Define how to run driver commands from settings panel
        this.settingsPanel.setManualSession(new SettingsPanel.ManualControlSession() {
            @Override
            public void sendRelativeMove(double dx, double dy) {
                ensureManualServer();
                sendServerCommand(String.format(java.util.Locale.US, "MOVE %f %f", dx, dy));
            }

            @Override
            public void sendPenCommand(String direction, int height) {
                ensureManualServer();
                // Note: The driver currently uses configured height from options
                // We could pass height in protocol if needed, but for now just UP/DOWN
                sendServerCommand("PEN " + direction);
            }

            @Override
            public void resetServer() {
                if (manualServerProcess != null && manualServerProcess.isAlive()) {
                    manualServerProcess.destroy();
                    manualServerProcess = null;
                    appendToConsole("[SRV] Server reset due to configuration change.");
                }
            }
        });

        this.settingsPanel.addVisualChangeListener(this::updateVisualSettings);

        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Top: Configuration
        JPanel configPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // JSON File
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.1;
        configPanel.add(new JLabel("JSON File:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.8;
        jsonField = new JTextField();
        configPanel.add(jsonField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.1;
        JButton selectJsonBtn = new JButton("Select...");
        selectJsonBtn.addActionListener(e -> selectFile());
        configPanel.add(selectJsonBtn, gbc);

        // Python Path
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.1;
        configPanel.add(new JLabel("Python Path:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.8;
        // Default to "python" but allow user to change to "python3" or absolute path
        String defaultPython = System.getProperty("os.name").toLowerCase().contains("win") ? "python" : "python3";
        pythonPathField = new JTextField(defaultPython);
        configPanel.add(pythonPathField, gbc);

        // Model Selection moved to SettingsPanel

        // Flags
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.1;
        configPanel.add(new JLabel("Options:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.9;
        gbc.gridwidth = 2;
        gbc.gridwidth = 2;
        JPanel checkBoxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        // Mock Mode moved to SettingsPanel
        verboseCheckBox = new JCheckBox("Verbose Logging", true); // Default TRUE for debugging
        checkBoxPanel.add(verboseCheckBox);
        configPanel.add(checkBoxPanel, gbc);

        // Speed Controls moved to SettingsPanel

        // Center: Split Pane for Console and Vis
        consoleArea = new JTextArea();
        consoleArea.setEditable(false);
        consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consoleArea.setBackground(Color.BLACK);
        consoleArea.setForeground(Color.GREEN);
        DefaultCaret caret = (DefaultCaret) consoleArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane consoleScroll = new JScrollPane(consoleArea);
        consoleScroll.setBorder(BorderFactory.createTitledBorder("Driver Output"));

        visPanel = new VisualizationPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, consoleScroll, visPanel);
        splitPane.setResizeWeight(0.4); // 40% console, 60% vis
        splitPane.setDividerLocation(350);

        // Bottom: Controls
        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        startButton = new JButton("Start Plot");
        startButton.addActionListener(e -> startProcess());

        stopButton = new JButton("Stop");
        stopButton.setEnabled(false);
        stopButton.addActionListener(e -> stopProcess());

        inputButton = new JButton("Confirm / Press Enter");
        inputButton.setEnabled(false);
        inputButton.addActionListener(e -> sendInput("\n"));

        controlPanel.add(startButton);
        controlPanel.add(inputButton);
        controlPanel.add(stopButton);

        add(configPanel, BorderLayout.NORTH);
        add(splitPane, BorderLayout.CENTER); // Changed from add(scrollPane)
        add(controlPanel, BorderLayout.SOUTH);
    }

    private void selectFile() {
        JFileChooser fc = new JFileChooser();
        File current = new File(jsonField.getText());
        if (current.exists())
            fc.setSelectedFile(current);
        else
            fc.setCurrentDirectory(new File("."));

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            jsonField.setText(fc.getSelectedFile().getAbsolutePath());
            // Pre-load visualization
            visPanel.loadFromJson(fc.getSelectedFile());
        }
    }

    private void startProcess() {
        String jsonPath = jsonField.getText();
        if (jsonPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a JSON file.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Reload vis just in case
        visPanel.loadFromJson(new File(jsonPath));
        updateVisualSettings();

        List<String> cmd = new ArrayList<>();
        cmd.add(pythonPathField.getText());
        cmd.add("driver/driver.py");
        cmd.add(jsonPath);
        if (settingsPanel.isMockMode()) {
            cmd.add("--mock");
        }
        if (settingsPanel.isInvertX()) {
            cmd.add("--invert-x");
        }
        if (settingsPanel.isInvertY()) {
            cmd.add("--invert-y");
        }
        if (settingsPanel.isSwapXY()) {
            cmd.add("--swap-xy");
        }
        if (verboseCheckBox.isSelected()) {
            cmd.add("--verbose");
        }

        cmd.add("--model");
        // Index 0 -> Model 1 (A4), Index 1 -> Model 2 (A3)
        cmd.add(String.valueOf(settingsPanel.getPlotterModelIndex() + 1));

        cmd.add("--speed-down");
        cmd.add(String.valueOf(settingsPanel.getDrawSpeed()));
        cmd.add("--speed-up");
        cmd.add(String.valueOf(settingsPanel.getTravelSpeed()));

        cmd.add("--pen-up");
        cmd.add(String.valueOf(settingsPanel.getPenUpHeight()));
        cmd.add("--pen-down");
        cmd.add(String.valueOf(settingsPanel.getPenDownHeight()));

        cmd.add("--report-position");
        cmd.add("--config");
        cmd.add(settingsPanel.getCurrentConfigFile().getAbsolutePath());

        consoleArea.setText("");
        appendToConsole("Starting driver...");
        appendToConsole("Command: " + String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        try {
            currentProcess = pb.start();
            processInputWriter = new BufferedWriter(new OutputStreamWriter(currentProcess.getOutputStream()));

            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            inputButton.setEnabled(true);

            // Disable config while running
            // Disable config while running
            settingsPanel.setSettingsEnabled(false);
            pythonPathField.setEnabled(false);

            // Output Reader Thread
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(currentProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String l = line;

                        // Parse Position Updates
                        if (l.startsWith("POS:")) {
                            // POS:X:10.5:Y:20.0
                            try {
                                String[] parts = l.split(":");
                                double x = Double.parseDouble(parts[2]);
                                double y = Double.parseDouble(parts[4]);
                                SwingUtilities.invokeLater(() -> visPanel.updatePosition(x, y));
                            } catch (Exception parseEx) {
                                // ignore parse errors
                            }
                            // Don't log POS lines to console to save spam
                            continue;
                        }

                        // Check for Connection Status
                        if (l.contains("INFO: Connection Successful")) {
                            updateStatus(true);
                        } else if (l.contains("ERROR: Could not connect") || l.contains("Connection Failed")) {
                            updateStatus(false);
                        }

                        SwingUtilities.invokeLater(() -> appendToConsole(l));
                    }
                } catch (IOException e) {
                    if (currentProcess != null && currentProcess.isAlive()) {
                        e.printStackTrace();
                    }
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        appendToConsole("--- Process Exited ---");
                        // If process dies, we lose connection
                        updateStatus(false);
                        processCleanup();
                    });
                }
            }).start();

        } catch (IOException e) {
            appendToConsole("Error starting process: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopProcess() {
        if (currentProcess != null && currentProcess.isAlive()) {
            currentProcess.destroy();
            appendToConsole("--- Process Killed by User ---");
        }
        processCleanup();
    }

    private void sendInput(String input) {
        if (currentProcess != null && currentProcess.isAlive() && processInputWriter != null) {
            try {
                processInputWriter.write(input);
                processInputWriter.flush();
                // Echo specific inputs if needed, but the driver usually echoes prompts
            } catch (IOException e) {
                appendToConsole("Error sending input: " + e.getMessage());
            }
        }
    }

    private void processCleanup() {
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        inputButton.setEnabled(false);

        // Re-enable config
        // Re-enable config
        settingsPanel.setSettingsEnabled(true);
        pythonPathField.setEnabled(true);

        currentProcess = null;
        processInputWriter = null;
    }

    private Process manualServerProcess;
    private BufferedWriter manualServerWriter;

    private void ensureManualServer() {
        if (manualServerProcess != null && manualServerProcess.isAlive()) {
            return;
        }

        // Start Server
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(pythonPathField.getText());
            cmd.add("driver/driver.py");
            cmd.add("--interactive-server");
            if (settingsPanel.isMockMode())
                cmd.add("--mock");
            if (verboseCheckBox.isSelected())
                cmd.add("--verbose");
            if (settingsPanel.isInvertX())
                cmd.add("--invert-x");
            if (settingsPanel.isInvertY())
                cmd.add("--invert-y");
            if (settingsPanel.isSwapXY())
                cmd.add("--swap-xy");

            cmd.add("--config");
            cmd.add(settingsPanel.getCurrentConfigFile().getAbsolutePath());

            // Model/Speed settings are set once at startup of server, might get stale if
            // user changes them?
            // User can just restart app or we can add protocol to update settings. For now
            // assume static.

            appendToConsole("Starting Manual Control Server...");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            manualServerProcess = pb.start();
            manualServerWriter = new BufferedWriter(new OutputStreamWriter(manualServerProcess.getOutputStream()));

            // Read output in thread
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(manualServerProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String l = line;

                        // Check Manual Server Connection
                        if (l.contains("INFO: Connection Successful")) {
                            updateStatus(true);
                        } else if (l.contains("ERROR: Could not connect")) {
                            updateStatus(false);
                        }

                        SwingUtilities.invokeLater(() -> appendToConsole("[SRV] " + l));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

        } catch (IOException e) {
            e.printStackTrace();
            appendToConsole("Failed to start server: " + e.getMessage());
        }
    }

    private void sendServerCommand(String cmd) {
        if (manualServerProcess != null && manualServerProcess.isAlive()) {
            try {
                manualServerWriter.write(cmd + "\n");
                manualServerWriter.flush();
            } catch (IOException e) {
                appendToConsole("Error sending to server: " + e.getMessage());
            }
        }
    }

    private void updateVisualSettings() {
        visPanel.setInvertX(settingsPanel.isVisualMirror());
        visPanel.setSwapXY(settingsPanel.isSwapXY());
    }

    private void appendToConsole(String text) {
        consoleArea.append(text + "\n");
    }

    private void updateStatus(boolean connected) {
        Window win = SwingUtilities.getWindowAncestor(this);
        if (win instanceof MainFrame) {
            ((MainFrame) win).setConnectionStatus(connected);
        }
    }
}

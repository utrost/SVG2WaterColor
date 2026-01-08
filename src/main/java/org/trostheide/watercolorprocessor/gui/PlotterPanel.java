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
    private final JCheckBox mockCheckBox;
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
        this.settingsPanel.setDriverRunner(args -> runDriverCommand(args));

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
        JPanel checkBoxPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        mockCheckBox = new JCheckBox("Mock Mode (No Hardware)", true);
        checkBoxPanel.add(mockCheckBox);
        checkBoxPanel.add(Box.createHorizontalStrut(15)); // Spacer
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

        List<String> cmd = new ArrayList<>();
        cmd.add(pythonPathField.getText());
        cmd.add("driver/driver.py");
        cmd.add(jsonPath);
        if (mockCheckBox.isSelected()) {
            cmd.add("--mock");
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
            mockCheckBox.setEnabled(false);
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

                        SwingUtilities.invokeLater(() -> appendToConsole(l));
                    }
                } catch (IOException e) {
                    if (currentProcess != null && currentProcess.isAlive()) {
                        e.printStackTrace();
                    }
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        appendToConsole("--- Process Exited ---");
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
        mockCheckBox.setEnabled(true);
        settingsPanel.setSettingsEnabled(true);
        pythonPathField.setEnabled(true);

        currentProcess = null;
        processInputWriter = null;
    }

    private void runDriverCommand(java.util.List<String> extraArgs) {
        // Construct minimal command for manual operation
        List<String> cmd = new ArrayList<>();
        cmd.add(pythonPathField.getText());
        cmd.add("driver/driver.py");
        // Manual mode doesn't need input file, but driver expects one if not manual
        // However, we made input optional in driver.py if --manual-pen is set.

        if (mockCheckBox.isSelected()) {
            cmd.add("--mock");
        }
        if (verboseCheckBox.isSelected()) {
            cmd.add("--verbose");
        }

        cmd.add("--model");
        cmd.add(String.valueOf(settingsPanel.getPlotterModelIndex() + 1));

        cmd.addAll(extraArgs);

        appendToConsole("Running Manual Command...");
        appendToConsole("Command: " + String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            // We just read output and wait, no complex interaction needed here
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        final String l = line;
                        SwingUtilities.invokeLater(() -> appendToConsole(l));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (IOException e) {
            appendToConsole("Error starting manual process: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void appendToConsole(String text) {
        consoleArea.append(text + "\n");
    }
}

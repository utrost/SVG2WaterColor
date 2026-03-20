package org.trostheide.watercolorprocessor.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;

public class ProcessorPanel extends JPanel {

    private final JTextField inputField;
    private final JTextField outputField;
    private final JSpinner maxDistSpinner;
    private final JSpinner curveStepSpinner;
    private final JTextField stationField;
    private final JComboBox<String> formatCombo;
    private final JSpinner paddingSpinner;
    private final JCheckBox mirrorCheckBox;
    private final JTextArea statusArea;
    private final JButton processBtn;
    private final JProgressBar progressBar;

    public ProcessorPanel(JTextArea statusArea) {

        this.statusArea = statusArea;
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        // === Top: File Selection + Settings in two grouped sections ===
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        // --- File Selection Group ---
        JPanel filePanel = new JPanel(new GridBagLayout());
        filePanel.setBorder(createSection("Input / Output"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Input File
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        filePanel.add(label("Input SVG"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        inputField = new JTextField();
        inputField.setToolTipText("Path to the SVG file with Inkscape layers");
        filePanel.add(inputField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JButton inputBtn = new JButton("Browse");
        inputBtn.addActionListener(e -> selectFile(inputField, false));
        filePanel.add(inputBtn, gbc);

        // Output File
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        filePanel.add(label("Output JSON"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        outputField = new JTextField();
        outputField.setToolTipText("Output path for the generated commands JSON");
        filePanel.add(outputField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JButton outputBtn = new JButton("Browse");
        outputBtn.addActionListener(e -> selectFile(outputField, true));
        filePanel.add(outputBtn, gbc);

        topPanel.add(filePanel);
        topPanel.add(Box.createVerticalStrut(4));

        // --- Processing Settings Group ---
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        settingsPanel.setBorder(createSection("Processing Settings"));
        GridBagConstraints sgbc = new GridBagConstraints();
        sgbc.insets = new Insets(4, 8, 4, 8);
        sgbc.fill = GridBagConstraints.HORIZONTAL;
        sgbc.anchor = GridBagConstraints.WEST;

        // Row 0: Max Distance + Curve Step (side by side)
        sgbc.gridx = 0; sgbc.gridy = 0; sgbc.weightx = 0;
        settingsPanel.add(label("Max Draw Dist (mm)"), sgbc);

        sgbc.gridx = 1; sgbc.weightx = 0.4;
        maxDistSpinner = new JSpinner(new SpinnerNumberModel(200.0, 10.0, 5000.0, 10.0));
        maxDistSpinner.setToolTipText("Maximum distance to draw before triggering a paint refill");
        settingsPanel.add(maxDistSpinner, sgbc);

        sgbc.gridx = 2; sgbc.weightx = 0;
        settingsPanel.add(label("Curve Step (mm)"), sgbc);

        sgbc.gridx = 3; sgbc.weightx = 0.4;
        curveStepSpinner = new JSpinner(new SpinnerNumberModel(0.05, 0.001, 10.0, 0.01));
        curveStepSpinner.setToolTipText("Resolution for linearizing curves (smaller = smoother)");
        settingsPanel.add(curveStepSpinner, sgbc);

        // Row 1: Station + Fit To Page (side by side)
        sgbc.gridx = 0; sgbc.gridy = 1; sgbc.weightx = 0;
        settingsPanel.add(label("Default Station"), sgbc);

        sgbc.gridx = 1; sgbc.weightx = 0.4;
        stationField = new JTextField("default_station");
        stationField.setToolTipText("Fallback station ID for layers without matching names");
        settingsPanel.add(stationField, sgbc);

        sgbc.gridx = 2; sgbc.weightx = 0;
        settingsPanel.add(label("Fit to Page"), sgbc);

        sgbc.gridx = 3; sgbc.weightx = 0.4;
        formatCombo = new JComboBox<>(new String[] { "None", "A5", "A4", "A3", "XL" });
        formatCombo.setSelectedItem("None");
        formatCombo.setToolTipText("Auto-scale the design to fit a standard paper format");
        settingsPanel.add(formatCombo, sgbc);

        // Row 2: Padding + Mirror
        sgbc.gridx = 0; sgbc.gridy = 2; sgbc.weightx = 0;
        settingsPanel.add(label("Padding (mm)"), sgbc);

        sgbc.gridx = 1; sgbc.weightx = 0.4;
        paddingSpinner = new JSpinner(new SpinnerNumberModel(10.0, 0.0, 100.0, 1.0));
        paddingSpinner.setToolTipText("Margin when auto-scaling to a paper format");
        settingsPanel.add(paddingSpinner, sgbc);

        sgbc.gridx = 2; sgbc.weightx = 0; sgbc.gridwidth = 2;
        mirrorCheckBox = new JCheckBox("Mirror Horizontally");
        mirrorCheckBox.setToolTipText("Flip the design along the X axis before processing");
        settingsPanel.add(mirrorCheckBox, sgbc);

        topPanel.add(settingsPanel);
        topPanel.add(Box.createVerticalStrut(8));

        // --- Action Bar ---
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
        processBtn = new JButton("Process SVG");
        processBtn.setFont(processBtn.getFont().deriveFont(Font.BOLD, 14f));
        processBtn.setPreferredSize(new Dimension(180, 38));
        processBtn.putClientProperty("JButton.buttonType", "roundRect");
        processBtn.addActionListener(e -> startProcessing());
        actionBar.add(processBtn);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(200, 8));
        actionBar.add(progressBar);

        topPanel.add(actionBar);

        add(topPanel, BorderLayout.NORTH);

        // === Center: Log Area ===
        statusArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(statusArea);
        scrollPane.setBorder(createSection("Processor Log"));
        add(scrollPane, BorderLayout.CENTER);
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setFont(l.getFont().deriveFont(12f));
        return l;
    }

    private TitledBorder createSection(String title) {
        TitledBorder border = BorderFactory.createTitledBorder(title);
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 12f));
        return border;
    }

    private void selectFile(JTextField target, boolean save) {
        JFileChooser fc = new JFileChooser();
        File current = new File(target.getText());
        if (current.exists())
            fc.setSelectedFile(current);
        else
            fc.setCurrentDirectory(new File("."));

        int res = save ? fc.showSaveDialog(this) : fc.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            target.setText(fc.getSelectedFile().getAbsolutePath());
            if (!save && outputField.getText().isEmpty()) {
                String in = fc.getSelectedFile().getAbsolutePath();
                if (in.toLowerCase().endsWith(".svg")) {
                    outputField.setText(in.substring(0, in.length() - 4) + ".json");
                } else {
                    outputField.setText(in + ".json");
                }
            }
        }
    }

    private void startProcessing() {
        String inPath = inputField.getText();
        String outPath = outputField.getText();

        if (inPath.isEmpty() || outPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select input and output files.", "Input Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        File inFile = new File(inPath);
        File outFile = new File(outPath);

        if (!inFile.exists()) {
            JOptionPane.showMessageDialog(this, "Input file does not exist.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        statusArea.setText("");
        processBtn.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);

        String selectedFormat = (String) formatCombo.getSelectedItem();
        if ("None".equals(selectedFormat))
            selectedFormat = null;

        ProcessingWorker worker = new ProcessingWorker(
                inFile,
                outFile,
                (Double) maxDistSpinner.getValue(),
                (Double) curveStepSpinner.getValue(),
                stationField.getText(),
                selectedFormat,
                (Double) paddingSpinner.getValue(),
                mirrorCheckBox.isSelected(),
                statusArea) {
            @Override
            protected void done() {
                super.done();
                processBtn.setEnabled(true);
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
            }
        };
        worker.execute();
    }
}

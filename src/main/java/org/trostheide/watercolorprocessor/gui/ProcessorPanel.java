package org.trostheide.watercolorprocessor.gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
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
    private final JTextArea statusArea;

    public ProcessorPanel(JTextArea statusArea) {

        this.statusArea = statusArea;
        setLayout(new GridBagLayout());
        setBorder(new EmptyBorder(10, 10, 10, 10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Row 0: Input File
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.1;
        add(new JLabel("Input SVG:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.8;
        inputField = new JTextField();
        add(inputField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.1;
        JButton inputBtn = new JButton("Select...");
        inputBtn.addActionListener(e -> selectFile(inputField, false));
        add(inputBtn, gbc);

        // Row 1: Output File
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.1;
        add(new JLabel("Output JSON:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.8;
        outputField = new JTextField();
        add(outputField, gbc);

        gbc.gridx = 2;
        gbc.weightx = 0.1;
        JButton outputBtn = new JButton("Select...");
        outputBtn.addActionListener(e -> selectFile(outputField, true));
        add(outputBtn, gbc);

        // Row 2: Max Distance
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.1;
        add(new JLabel("Max Draw Dist (mm):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.9;
        gbc.gridwidth = 2;
        maxDistSpinner = new JSpinner(new SpinnerNumberModel(200.0, 10.0, 5000.0, 10.0));
        add(maxDistSpinner, gbc);

        // Row 3: Curve Step
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.1;
        gbc.gridwidth = 1;
        add(new JLabel("Curve Step (mm):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.9;
        gbc.gridwidth = 2;
        // High Resolution Default: 0.05mm (was 0.5mm)
        // Step size: 0.01mm for fine tuning
        curveStepSpinner = new JSpinner(new SpinnerNumberModel(0.05, 0.001, 10.0, 0.01));
        add(curveStepSpinner, gbc);

        // Row 4: Default Station
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0.1;
        gbc.gridwidth = 1;
        add(new JLabel("Default Station ID:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.9;
        gbc.gridwidth = 2;
        stationField = new JTextField("default_station");
        add(stationField, gbc);

        // Row 5: Fit To Format
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0.1;
        gbc.gridwidth = 1;
        add(new JLabel("Fit to Page:"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.9;
        gbc.gridwidth = 2;
        formatCombo = new JComboBox<>(new String[] { "None", "A5", "A4", "A3", "XL" });
        formatCombo.setSelectedItem("None");
        add(formatCombo, gbc);

        // Row 6: Padding
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.weightx = 0.1;
        gbc.gridwidth = 1;
        add(new JLabel("Padding (mm):"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.9;
        gbc.gridwidth = 2;
        paddingSpinner = new JSpinner(new SpinnerNumberModel(10.0, 0.0, 100.0, 1.0));
        add(paddingSpinner, gbc);

        // Row 7: Process Button
        gbc.gridx = 0;
        gbc.gridy = 7;
        gbc.weightx = 1.0;
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.CENTER;
        JButton processBtn = new JButton("Process SVG");
        processBtn.setFont(processBtn.getFont().deriveFont(Font.BOLD, 14f));
        processBtn.setPreferredSize(new Dimension(150, 40));
        processBtn.addActionListener(e -> startProcessing());
        add(processBtn, gbc);

        // Row 8: Log Area
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.weightx = 1.0;
        gbc.weighty = 1.0; // Fill remaining vertical space
        gbc.gridwidth = 3;
        gbc.fill = GridBagConstraints.BOTH;
        JScrollPane scrollPane = new JScrollPane(statusArea);
        scrollPane.setBorder(BorderFactory.createTitledBorder("Processor Log"));
        add(scrollPane, gbc);
    }

    private void selectFile(JTextField target, boolean save) {
        JFileChooser fc = new JFileChooser();
        // pre-select current directory if possible
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
                statusArea);
        worker.execute();
    }
}

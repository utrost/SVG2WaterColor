package org.trostheide.watercolorprocessor.gui;

import org.trostheide.watercolorprocessor.ProcessorService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.io.File;
import java.util.function.Consumer;

public class SvgDrawPanel extends JPanel {

    private final JTextField svgField;
    private final JSpinner curveStepSpinner;
    private final JComboBox<String> formatCombo;
    private final JSpinner paddingSpinner;
    private final JCheckBox mirrorCheckBox;
    private final JButton convertButton;
    private final JTextArea logArea;
    private final JProgressBar progressBar;
    private final SettingsPanel settingsPanel;

    private Consumer<File> onJsonReady;

    public SvgDrawPanel(SettingsPanel settingsPanel) {
        this.settingsPanel = settingsPanel;
        setLayout(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(12, 12, 12, 12));

        // === Top: Settings ===
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        // File selection
        JPanel filePanel = new JPanel(new GridBagLayout());
        filePanel.setBorder(createSection("SVG Input"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 8, 4, 8);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        filePanel.add(label("SVG File"), gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        svgField = new JTextField();
        svgField.setToolTipText("Path to the SVG file to draw (supports Inkscape layers)");
        filePanel.add(svgField, gbc);

        gbc.gridx = 2; gbc.weightx = 0;
        JButton browseBtn = new JButton("Browse");
        browseBtn.addActionListener(e -> selectSvgFile());
        filePanel.add(browseBtn, gbc);

        topPanel.add(filePanel);
        topPanel.add(Box.createVerticalStrut(4));

        // Drawing settings
        JPanel drawSettings = new JPanel(new GridBagLayout());
        drawSettings.setBorder(createSection("Drawing Settings"));
        GridBagConstraints sgbc = new GridBagConstraints();
        sgbc.insets = new Insets(4, 8, 4, 8);
        sgbc.fill = GridBagConstraints.HORIZONTAL;
        sgbc.anchor = GridBagConstraints.WEST;

        sgbc.gridx = 0; sgbc.gridy = 0; sgbc.weightx = 0;
        drawSettings.add(label("Curve Step (mm)"), sgbc);

        sgbc.gridx = 1; sgbc.weightx = 0.4;
        curveStepSpinner = new JSpinner(new SpinnerNumberModel(0.05, 0.001, 10.0, 0.01));
        curveStepSpinner.setToolTipText("Resolution for linearizing curves (smaller = smoother)");
        drawSettings.add(curveStepSpinner, sgbc);

        sgbc.gridx = 2; sgbc.weightx = 0;
        drawSettings.add(label("Fit to"), sgbc);

        sgbc.gridx = 3; sgbc.weightx = 0.4;
        formatCombo = new JComboBox<>(new String[]{"None", "Machine", "A5", "A4", "A3", "XL"});
        formatCombo.setSelectedItem("Machine");
        formatCombo.setToolTipText("Auto-scale: 'Machine' uses current machine bed dimensions from settings");
        drawSettings.add(formatCombo, sgbc);

        sgbc.gridx = 0; sgbc.gridy = 1; sgbc.weightx = 0;
        drawSettings.add(label("Padding (mm)"), sgbc);

        sgbc.gridx = 1; sgbc.weightx = 0.4;
        paddingSpinner = new JSpinner(new SpinnerNumberModel(10.0, 0.0, 100.0, 1.0));
        paddingSpinner.setToolTipText("Margin when auto-scaling to a paper format");
        drawSettings.add(paddingSpinner, sgbc);

        sgbc.gridx = 2; sgbc.weightx = 0; sgbc.gridwidth = 2;
        mirrorCheckBox = new JCheckBox("Mirror Horizontally");
        mirrorCheckBox.setToolTipText("Flip the design along the X axis before processing");
        drawSettings.add(mirrorCheckBox, sgbc);

        topPanel.add(drawSettings);
        topPanel.add(Box.createVerticalStrut(8));

        // Action bar
        JPanel actionBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 4));
        convertButton = new JButton("Convert & Plot");
        convertButton.setFont(convertButton.getFont().deriveFont(Font.BOLD, 14f));
        convertButton.setPreferredSize(new Dimension(180, 38));
        convertButton.putClientProperty("JButton.buttonType", "roundRect");
        convertButton.addActionListener(e -> startConversion());
        actionBar.add(convertButton);

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(false);
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(200, 8));
        actionBar.add(progressBar);

        topPanel.add(actionBar);

        add(topPanel, BorderLayout.NORTH);

        // === Center: Log ===
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("JetBrains Mono", Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(createSection("Conversion Log"));
        add(scrollPane, BorderLayout.CENTER);
    }

    public void setOnJsonReady(Consumer<File> callback) {
        this.onJsonReady = callback;
    }

    private void selectSvgFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("SVG Files", "svg"));
        File current = new File(svgField.getText());
        if (current.exists())
            fc.setSelectedFile(current);
        else
            fc.setCurrentDirectory(new File("."));

        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            svgField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void startConversion() {
        String svgPath = svgField.getText().trim();
        if (svgPath.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select an SVG file.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File svgFile = new File(svgPath);
        if (!svgFile.exists()) {
            JOptionPane.showMessageDialog(this, "File not found: " + svgPath, "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        logArea.setText("");
        convertButton.setEnabled(false);
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);

        String selectedFormat = (String) formatCombo.getSelectedItem();
        if ("None".equals(selectedFormat)) {
            selectedFormat = null;
        } else if ("Machine".equals(selectedFormat)) {
            double mw = settingsPanel.getMachineWidth();
            double mh = settingsPanel.getMachineHeight();
            selectedFormat = String.format(java.util.Locale.US, "%.0fx%.0f", mw, mh);
        }

        double curveStep = (Double) curveStepSpinner.getValue();
        double padding = (Double) paddingSpinner.getValue();
        boolean mirror = mirrorCheckBox.isSelected();
        String format = selectedFormat;

        new SwingWorker<File, String>() {
            @Override
            protected File doInBackground() throws Exception {
                publish("Converting SVG for pen drawing (no refills)...");
                publish("Input: " + svgFile.getAbsolutePath());

                File tempJson = File.createTempFile("svgdraw_", ".json");
                tempJson.deleteOnExit();

                ProcessorService service = new ProcessorService();
                service.process(svgFile, tempJson, 0, "pen", curveStep, format, padding, mirror);

                publish("Conversion complete: " + tempJson.getAbsolutePath());
                return tempJson;
            }

            @Override
            protected void process(java.util.List<String> chunks) {
                for (String line : chunks) {
                    logArea.append(line + "\n");
                }
            }

            @Override
            protected void done() {
                convertButton.setEnabled(true);
                progressBar.setIndeterminate(false);
                progressBar.setVisible(false);
                try {
                    File jsonFile = get();
                    logArea.append("Ready to plot.\n");
                    if (onJsonReady != null) {
                        onJsonReady.accept(jsonFile);
                    }
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    logArea.append("Error: " + cause.getMessage() + "\n");
                    JOptionPane.showMessageDialog(SvgDrawPanel.this,
                            "Error: " + cause.getMessage(), "Conversion Failed",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
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
}

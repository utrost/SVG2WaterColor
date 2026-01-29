package org.trostheide.watercolorprocessor.gui;

import org.trostheide.watercolorprocessor.ProcessorService;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class ProcessingWorker extends SwingWorker<String, String> {

    private final File inputFile;
    private final File outputFile;
    private final double maxDistance;
    private final double curveStep;
    private final String stationId;
    private final JTextArea statusArea;

    private final String fitToFormat;
    private final double padding;
    private final boolean mirror;

    public ProcessingWorker(File inputFile, File outputFile, double maxDistance, double curveStep, String stationId,
            String fitToFormat, double padding, boolean mirror, JTextArea statusArea) {
        this.inputFile = inputFile;
        this.outputFile = outputFile;
        this.maxDistance = maxDistance;
        this.curveStep = curveStep;
        this.stationId = stationId;
        this.fitToFormat = fitToFormat;
        this.padding = padding;
        this.mirror = mirror;
        this.statusArea = statusArea;
    }

    @Override
    protected String doInBackground() throws Exception {
        publish("Starting processing...");
        publish("Input: " + inputFile.getAbsolutePath());
        publish("Output: " + outputFile.getAbsolutePath());
        publish(String.format("Max Distance: %.2f mm", maxDistance));

        try {
            ProcessorService service = new ProcessorService();
            // Since the Service logs to SLF4J, we won't capture internal logs easily
            // without a custom appender.
            // For now, we just run it and catch exceptions.
            service.process(inputFile, outputFile, maxDistance, stationId, curveStep, fitToFormat, padding, mirror);
            return "Success! Output written to: " + outputFile.getName();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @Override
    protected void process(List<String> chunks) {
        for (String line : chunks) {
            statusArea.append(line + "\n");
        }
    }

    @Override
    protected void done() {
        try {
            String result = get();
            statusArea.append("Done: " + result + "\n");
            JOptionPane.showMessageDialog(null, result, "Processing Complete", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            statusArea.append("Error: " + e.getCause().getMessage() + "\n");
            JOptionPane.showMessageDialog(null, "Error: " + e.getCause().getMessage(), "Processing Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
